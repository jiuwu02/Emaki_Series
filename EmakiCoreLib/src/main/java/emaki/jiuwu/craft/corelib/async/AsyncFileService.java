package emaki.jiuwu.craft.corelib.async;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler.TaskPriority;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;

public final class AsyncFileService implements AutoCloseable {

    public record WriteRequest(Path path, String taskName, Runnable task) {
    }

    private enum ServiceState {
        OPEN,
        QUIESCING,
        CLOSED
    }

    private enum ScopeState {
        OPEN,
        SEALED,
        CLOSED
    }

    public record DrainResult(boolean drained, int pendingOperations, List<Throwable> failures) {

        public DrainResult {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }
    }

    private static final long RETRY_DELAY_MILLIS = 100L;
    private static final int MAX_RECORDED_FAILURES = 32;

    private final AsyncTaskScheduler scheduler;
    private final int maxRetries;
    private final PerformanceMonitor performanceMonitor;
    private final ConcurrentMap<Path, CompletableFuture<Void>> physicalTails = new ConcurrentHashMap<>();
    private final Set<FileScope> scopes = ConcurrentHashMap.newKeySet();
    private final Object submissionLock = new Object();
    private final OperationTracker allOperations = new OperationTracker();
    private final AtomicReference<ServiceState> state = new AtomicReference<>(ServiceState.OPEN);
    private final FileScope defaultScope;

    public AsyncFileService(AsyncTaskScheduler scheduler) {
        this(scheduler, 3, null);
    }

    public AsyncFileService(AsyncTaskScheduler scheduler, int retryAttempts, PerformanceMonitor performanceMonitor) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.maxRetries = Math.max(0, retryAttempts - 1);
        this.performanceMonitor = performanceMonitor;
        this.defaultScope = createScope("corelib-default");
    }

    public FileScope defaultScope() {
        return defaultScope;
    }

    public FileScope openScope(String ownerName) {
        synchronized (submissionLock) {
            ensureOpen();
            return createScope(ownerName);
        }
    }

    public <T> CompletableFuture<T> read(String taskName, Supplier<T> action) {
        return defaultScope.read(taskName, action);
    }

    public <T> CompletableFuture<T> read(Path path, String taskName, Supplier<T> action) {
        return defaultScope.read(path, taskName, action);
    }

    public CompletableFuture<Void> write(Path path, String taskName, Runnable action) {
        if (path == null) {
            return CompletableFuture.completedFuture(null);
        }
        return defaultScope.write(path, taskName, action);
    }

    public CompletableFuture<Void> writeBatch(List<WriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (WriteRequest request : requests) {
            if (request != null && request.path() != null && request.task() != null) {
                futures.add(write(request.path(), request.taskName(), request.task()));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<Void> waitForIdle() {
        return allOperations.whenIdle();
    }

    public int pendingWriteCount() {
        return allOperations.pendingCount();
    }

    public DrainResult closeAndDrain(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        synchronized (submissionLock) {
            ServiceState current = state.get();
            if (current == ServiceState.OPEN) {
                state.set(ServiceState.QUIESCING);
            }
            for (FileScope scope : scopes) {
                scope.sealLocked();
            }
        }
        DrainResult result = allOperations.await(timeout, unit);
        if (result.drained()) {
            synchronized (submissionLock) {
                state.set(ServiceState.CLOSED);
                for (FileScope scope : scopes) {
                    scope.closeLocked();
                }
                physicalTails.clear();
            }
        }
        return result;
    }

    public void close(long timeout, TimeUnit unit) {
        closeAndDrain(timeout, unit);
    }

    @Override
    public void close() {
        closeAndDrain(30L, TimeUnit.SECONDS);
    }

    private FileScope createScope(String ownerName) {
        String normalized = ownerName == null || ownerName.isBlank() ? "anonymous" : ownerName.trim();
        FileScope scope = new FileScope(normalized);
        scopes.add(scope);
        return scope;
    }

    private <T> CompletableFuture<T> submitDirect(FileScope scope, String taskName, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        synchronized (submissionLock) {
            if (!canSubmit(scope)) {
                return rejectedFuture(scope);
            }
            CompletableFuture<T> operation = executeWithRetry(safeTaskName(taskName), action, 0);
            track(scope, operation);
            return operation;
        }
    }

    private <T> CompletableFuture<T> submitPhysical(FileScope scope,
            Path path,
            String taskName,
            Supplier<T> action) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(action, "action");
        synchronized (submissionLock) {
            if (!canSubmit(scope)) {
                return rejectedFuture(scope);
            }
            Path physicalPath = physicalPath(path);
            AtomicReference<CompletableFuture<T>> operationReference = new AtomicReference<>();
            AtomicReference<CompletableFuture<Void>> tailReference = new AtomicReference<>();
            physicalTails.compute(physicalPath, (_, predecessor) -> {
                CompletableFuture<Void> ready = predecessor == null
                        ? CompletableFuture.completedFuture(null)
                        : predecessor.handle((_, _) -> null);
                CompletableFuture<T> operation = ready.thenCompose(_ ->
                        executeWithRetry(safeTaskName(taskName), action, 0));
                CompletableFuture<Void> tail = operation.handle((_, _) -> null);
                operationReference.set(operation);
                tailReference.set(tail);
                return tail;
            });
            CompletableFuture<T> operation = operationReference.get();
            CompletableFuture<Void> tail = tailReference.get();
            tail.whenComplete((_, _) -> physicalTails.remove(physicalPath, tail));
            track(scope, operation);
            return operation;
        }
    }

    private <T> CompletableFuture<T> executeWithRetry(String taskName, Supplier<T> action, int attempt) {
        CompletableFuture<T> current = scheduler.supplyAsync(taskName, TaskPriority.NORMAL, 0L, () -> {
            long startedAt = System.nanoTime();
            try {
                T value = action.get();
                recordPerformance("async-file:" + taskName, System.nanoTime() - startedAt, true);
                return value;
            } catch (Throwable throwable) {
                recordPerformance("async-file:" + taskName, System.nanoTime() - startedAt, false);
                throw throwable;
            }
        });
        return current.handle((value, throwable) -> {
            if (throwable == null) {
                return CompletableFuture.completedFuture(value);
            }
            Throwable cause = AsyncFailures.unwrap(throwable);
            if (attempt >= maxRetries || cause instanceof RejectedExecutionException) {
                return CompletableFuture.<T>failedFuture(cause);
            }
            long delayMillis = RETRY_DELAY_MILLIS * (1L << attempt);
            return scheduler.schedule(
                    taskName + "-retry-" + (attempt + 1),
                    delayMillis,
                    0L,
                    TaskPriority.LOW,
                    () -> null
            ).thenCompose(_ -> executeWithRetry(taskName, action, attempt + 1));
        }).thenCompose(future -> future);
    }

    private void track(FileScope scope, CompletableFuture<?> operation) {
        scope.operations.accepted(operation);
        allOperations.accepted(operation);
    }

    private boolean canSubmit(FileScope scope) {
        return state.get() == ServiceState.OPEN && scope.state.get() == ScopeState.OPEN;
    }

    private void ensureOpen() {
        if (state.get() != ServiceState.OPEN) {
            throw new RejectedExecutionException("Async file service is shutting down");
        }
    }

    private <T> CompletableFuture<T> rejectedFuture(FileScope scope) {
        return CompletableFuture.failedFuture(new RejectedExecutionException(
                "Async file scope is sealed: " + scope.ownerName));
    }

    private Path physicalPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            if (Files.exists(absolute)) {
                return absolute.toRealPath();
            }
            List<Path> missingSegments = new ArrayList<>();
            Path ancestor = absolute;
            while (ancestor != null && !Files.exists(ancestor)) {
                Path fileName = ancestor.getFileName();
                if (fileName != null) {
                    missingSegments.add(fileName);
                }
                ancestor = ancestor.getParent();
            }
            if (ancestor == null) {
                return absolute;
            }
            Path resolved = ancestor.toRealPath();
            for (int index = missingSegments.size() - 1; index >= 0; index--) {
                resolved = resolved.resolve(missingSegments.get(index));
            }
            return resolved.normalize();
        } catch (IOException | SecurityException ignored) {
            return absolute;
        }
    }

    private void recordPerformance(String metricKey, long durationNanos, boolean success) {
        if (performanceMonitor != null) {
            performanceMonitor.record(metricKey, durationNanos, success);
        }
    }

    private String safeTaskName(String taskName) {
        return taskName == null || taskName.isBlank() ? "async-file" : taskName.trim();
    }

    public final class FileScope {

        private final String ownerName;
        private final AtomicReference<ScopeState> state = new AtomicReference<>(ScopeState.OPEN);
        private final OperationTracker operations = new OperationTracker();

        private FileScope(String ownerName) {
            this.ownerName = ownerName;
        }

        public String ownerName() {
            return ownerName;
        }

        public <T> CompletableFuture<T> read(String taskName, Supplier<T> action) {
            return submitDirect(this, taskName, action);
        }

        public <T> CompletableFuture<T> read(Path path, String taskName, Supplier<T> action) {
            return submitPhysical(this, path, taskName, action);
        }

        public CompletableFuture<Void> write(Path path, String taskName, Runnable action) {
            Objects.requireNonNull(action, "action");
            return submitPhysical(this, path, taskName, () -> {
                action.run();
                return null;
            });
        }

        public CompletableFuture<Void> waitForIdle() {
            return operations.whenIdle();
        }

        public int pendingOperationCount() {
            return operations.pendingCount();
        }

        public boolean acceptingOperations() {
            return state.get() == ScopeState.OPEN && AsyncFileService.this.state.get() == ServiceState.OPEN;
        }

        public DrainResult sealAndDrain(long timeout, TimeUnit unit) {
            Objects.requireNonNull(unit, "unit");
            synchronized (submissionLock) {
                sealLocked();
            }
            DrainResult result = operations.await(timeout, unit);
            if (result.drained()) {
                synchronized (submissionLock) {
                    closeLocked();
                }
            }
            return result;
        }

        private void sealLocked() {
            state.compareAndSet(ScopeState.OPEN, ScopeState.SEALED);
        }

        private void closeLocked() {
            state.set(ScopeState.CLOSED);
        }
    }

    private static final class OperationTracker {

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition idle = lock.newCondition();
        private final List<CompletableFuture<Void>> idleWaiters = new ArrayList<>();
        private final List<Throwable> failures = new ArrayList<>();
        private int pending;

        private void accepted(CompletableFuture<?> operation) {
            lock.lock();
            try {
                pending++;
            } finally {
                lock.unlock();
            }
            operation.whenComplete((_, throwable) -> completed(throwable));
        }

        private void completed(Throwable throwable) {
            List<CompletableFuture<Void>> waiters = List.of();
            lock.lock();
            try {
                if (throwable != null && failures.size() < MAX_RECORDED_FAILURES) {
                    failures.add(AsyncFailures.unwrap(throwable));
                }
                pending = Math.max(0, pending - 1);
                if (pending == 0) {
                    idle.signalAll();
                    if (!idleWaiters.isEmpty()) {
                        waiters = List.copyOf(idleWaiters);
                        idleWaiters.clear();
                    }
                }
            } finally {
                lock.unlock();
            }
            for (CompletableFuture<Void> waiter : waiters) {
                waiter.complete(null);
            }
        }

        private CompletableFuture<Void> whenIdle() {
            lock.lock();
            try {
                if (pending == 0) {
                    return CompletableFuture.completedFuture(null);
                }
                CompletableFuture<Void> waiter = new CompletableFuture<>();
                idleWaiters.add(waiter);
                return waiter;
            } finally {
                lock.unlock();
            }
        }

        private int pendingCount() {
            lock.lock();
            try {
                return pending;
            } finally {
                lock.unlock();
            }
        }

        private DrainResult await(long timeout, TimeUnit unit) {
            long remainingNanos = Math.max(0L, unit.toNanos(timeout));
            boolean interrupted = false;
            lock.lock();
            try {
                while (pending > 0 && remainingNanos > 0L) {
                    try {
                        remainingNanos = idle.awaitNanos(remainingNanos);
                    } catch (InterruptedException exception) {
                        interrupted = true;
                        if (failures.size() < MAX_RECORDED_FAILURES) {
                            failures.add(exception);
                        }
                        break;
                    }
                }
                return new DrainResult(pending == 0, pending, failures);
            } finally {
                lock.unlock();
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
