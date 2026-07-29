package emaki.jiuwu.craft.corelib.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.DrainResult;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;

public final class CorePluginLifecycle {

    public enum State {
        NEW,
        RUNNING,
        QUIESCING,
        CLOSED
    }

    public record ShutdownReport(boolean fileOperationsDrained,
                                 boolean schedulerTerminated,
                                 int pendingFileOperations,
                                 List<Throwable> fileFailures) {

        public ShutdownReport {
            fileFailures = fileFailures == null ? List.of() : List.copyOf(fileFailures);
        }

        public boolean clean() {
            return fileOperationsDrained && schedulerTerminated && fileFailures.isEmpty();
        }
    }

    private final Supplier<? extends CompletionStage<?>> preDrainShutdown;
    private final Map<String, Future<?>> dependentShutdowns = new LinkedHashMap<>();

    private State state = State.NEW;
    private AsyncFileService fileService;
    private AsyncTaskScheduler taskScheduler;
    private ShutdownReport lastReport = new ShutdownReport(true, true, 0, List.of());
    private CompletableFuture<ShutdownReport> shutdownFuture;

    public CorePluginLifecycle() {
        this(() -> CompletableFuture.completedFuture(null));
    }

    public CorePluginLifecycle(Supplier<? extends CompletionStage<?>> preDrainShutdown) {
        this.preDrainShutdown = Objects.requireNonNull(preDrainShutdown, "preDrainShutdown");
    }

    public synchronized void start(AsyncFileService fileService, AsyncTaskScheduler taskScheduler) {
        if (state != State.NEW) {
            throw new IllegalStateException("Core plugin lifecycle already started: " + state);
        }
        this.fileService = Objects.requireNonNull(fileService, "fileService");
        this.taskScheduler = Objects.requireNonNull(taskScheduler, "taskScheduler");
        state = State.RUNNING;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized boolean registerDependentShutdown(String ownerKey, CompletionStage<?> shutdown) {
        Objects.requireNonNull(shutdown, "shutdown");
        return registerDependentShutdownFuture(ownerKey, futureForStage(shutdown));
    }

    public synchronized boolean registerDependentShutdownFuture(String ownerKey, Future<?> shutdown) {
        Objects.requireNonNull(shutdown, "shutdown");
        if (state != State.RUNNING || shutdownFuture != null) {
            return false;
        }
        String key = normalizeOwnerKey(ownerKey);
        if (dependentShutdowns.containsKey(key)) {
            return false;
        }
        dependentShutdowns.put(key, shutdown);
        return true;
    }

    public synchronized CompletableFuture<ShutdownReport> shutdownAsync(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (shutdownFuture != null) {
            return shutdownFuture;
        }
        state = State.QUIESCING;
        long timeoutNanos = Math.max(1L, unit.toNanos(timeout));
        Map<String, Future<?>> dependents = new LinkedHashMap<>(dependentShutdowns);
        dependentShutdowns.clear();
        CompletableFuture<ShutdownReport> created = new CompletableFuture<>();
        shutdownFuture = created;
        Thread finalizer = new Thread(
                () -> finalizeShutdown(timeoutNanos, dependents, created),
                "emaki-corelib-shutdown-finalizer"
        );
        finalizer.setDaemon(true);
        try {
            finalizer.start();
        } catch (Throwable throwable) {
            ShutdownReport report = new ShutdownReport(false, false,
                    fileService == null ? 0 : fileService.pendingWriteCount(), List.of(throwable));
            finishShutdown(created, report);
        }
        return created;
    }

    public synchronized ShutdownReport shutdown(long timeout, TimeUnit unit) {
        shutdownAsync(timeout, unit);
        return lastReport;
    }

    private void finalizeShutdown(long timeoutNanos,
            Map<String, Future<?>> dependents,
            CompletableFuture<ShutdownReport> completion) {
        long deadline = deadlineAfter(timeoutNanos);
        List<Throwable> failures = new ArrayList<>();
        awaitDependents(dependents, deadline, failures);
        awaitPreDrainShutdown(deadline, failures);

        DrainResult fileResult;
        try {
            fileResult = fileService == null
                    ? new DrainResult(true, 0, List.of())
                    : fileService.closeAndDrain(remainingNanos(deadline), TimeUnit.NANOSECONDS);
        } catch (Throwable throwable) {
            failures.add(AsyncFailures.unwrap(throwable));
            fileResult = new DrainResult(false,
                    fileService == null ? 0 : fileService.pendingWriteCount(), List.of());
        }
        failures.addAll(fileResult.failures());

        boolean schedulerTerminated;
        try {
            schedulerTerminated = taskScheduler == null
                    || taskScheduler.shutdownGracefully(remainingNanos(deadline), TimeUnit.NANOSECONDS);
        } catch (Throwable throwable) {
            failures.add(AsyncFailures.unwrap(throwable));
            schedulerTerminated = false;
        }

        ShutdownReport report = new ShutdownReport(
                fileResult.drained(),
                schedulerTerminated,
                fileResult.pendingOperations(),
                failures
        );
        finishShutdown(completion, report);
    }

    private void awaitDependents(Map<String, Future<?>> dependents,
            long deadline,
            List<Throwable> failures) {
        for (Map.Entry<String, Future<?>> entry : dependents.entrySet()) {
            if (!awaitFuture("Dependent shutdown " + entry.getKey(), entry.getValue(), deadline, failures)) {
                return;
            }
        }
    }

    private void awaitPreDrainShutdown(long deadline, List<Throwable> failures) {
        CompletableFuture<? extends CompletionStage<?>> invocation = CompletableFuture.supplyAsync(preDrainShutdown);
        CompletionStage<?> stage;
        try {
            stage = invocation.get(remainingNanos(deadline), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            failures.add(new TimeoutException("Core runtime finalization dispatch timed out"));
            return;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failures.add(exception);
            return;
        } catch (ExecutionException exception) {
            failures.add(AsyncFailures.unwrap(exception));
            return;
        } catch (Throwable throwable) {
            failures.add(AsyncFailures.unwrap(throwable));
            return;
        }
        if (stage != null) {
            awaitFuture("Core runtime finalization", futureForStage(stage), deadline, failures);
        }
    }

    private static CompletableFuture<Void> futureForStage(CompletionStage<?> stage) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            stage.whenComplete((ignored, throwable) -> {
                if (throwable == null) {
                    completion.complete(null);
                } else {
                    completion.completeExceptionally(AsyncFailures.unwrap(throwable));
                }
            });
        } catch (Throwable throwable) {
            completion.completeExceptionally(AsyncFailures.unwrap(throwable));
        }
        return completion;
    }

    private boolean awaitFuture(String label,
            Future<?> future,
            long deadline,
            List<Throwable> failures) {
        long remainingNanos = remainingNanos(deadline);
        try {
            future.get(remainingNanos, TimeUnit.NANOSECONDS);
            return true;
        } catch (TimeoutException exception) {
            failures.add(new TimeoutException(label + " timed out"));
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failures.add(exception);
            return false;
        } catch (ExecutionException exception) {
            failures.add(AsyncFailures.unwrap(exception));
            return true;
        } catch (CancellationException exception) {
            failures.add(exception);
            return true;
        } catch (Throwable throwable) {
            failures.add(AsyncFailures.unwrap(throwable));
            return true;
        }
    }

    private synchronized void finishShutdown(CompletableFuture<ShutdownReport> completion,
            ShutdownReport report) {
        lastReport = report;
        state = State.CLOSED;
        completion.complete(report);
    }

    private static String normalizeOwnerKey(String ownerKey) {
        String normalized = Objects.requireNonNull(ownerKey, "ownerKey").trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("ownerKey");
        }
        return normalized;
    }

    private static long deadlineAfter(long timeoutNanos) {
        long now = System.nanoTime();
        if (timeoutNanos >= Long.MAX_VALUE - now) {
            return Long.MAX_VALUE;
        }
        return now + timeoutNanos;
    }

    private static long remainingNanos(long deadline) {
        if (deadline == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, deadline - System.nanoTime());
    }
}
