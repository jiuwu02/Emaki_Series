package emaki.jiuwu.craft.corelib.async;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;

public final class AsyncFileService {

    public record WriteRequest(Path path, String taskName, Runnable task) {

    }

    private final AsyncTaskScheduler scheduler;
    private final Map<Path, CompletableFuture<Void>> pendingWrites = new ConcurrentHashMap<>();
    private final int retryAttempts;
    private final PerformanceMonitor performanceMonitor;

    public AsyncFileService(AsyncTaskScheduler scheduler) {
        this(scheduler, 3, null);
    }

    public AsyncFileService(AsyncTaskScheduler scheduler, int retryAttempts, PerformanceMonitor performanceMonitor) {
        this.scheduler = scheduler;
        this.retryAttempts = Math.max(1, retryAttempts);
        this.performanceMonitor = performanceMonitor;
    }

    public <T> CompletableFuture<T> read(String taskName, Supplier<T> supplier) {
        if (scheduler == null) {
            try {
                long startedAt = System.nanoTime();
                T value = supplier.get();
                recordPerformance("async-file:read:" + safeTaskName(taskName), System.nanoTime() - startedAt, true);
                return CompletableFuture.completedFuture(value);
            } catch (Exception throwable) {
                recordPerformance("async-file:read:" + safeTaskName(taskName), 0L, false);
                CompletableFuture<T> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(throwable);
                return failedFuture;
            }
        }
        return scheduler.supplyAsync(taskName, () -> executeWithRetry("async-file:read:" + safeTaskName(taskName), supplier));
    }

    public CompletableFuture<Void> write(Path path, String taskName, Runnable task) {
        if (path == null) {
            return CompletableFuture.completedFuture(null);
        }
        Path normalized = path.toAbsolutePath().normalize();
        CompletableFuture<Void> scheduled = pendingWrites.compute(normalized, (_, previous) -> {
            CompletableFuture<Void> base = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.exceptionally(throwable -> null);
            return base.thenCompose(unused -> runWrite(taskName, task));
        });
        scheduled.whenComplete((unused, throwable) -> pendingWrites.compute(normalized, (_, current) -> current == scheduled ? null : current));
        return scheduled;
    }

    public CompletableFuture<Void> writeBatch(List<WriteRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (WriteRequest request : requests) {
            if (request == null || request.task() == null) {
                continue;
            }
            futures.add(write(request.path(), request.taskName(), request.task()));
        }
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<Void> waitForIdle() {
        ArrayList<CompletableFuture<Void>> futures = new ArrayList<>(pendingWrites.values());
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> runWrite(String taskName, Runnable task) {
        if (scheduler == null) {
            try {
                long startedAt = System.nanoTime();
                task.run();
                recordPerformance("async-file:write:" + safeTaskName(taskName), System.nanoTime() - startedAt, true);
                return CompletableFuture.completedFuture(null);
            } catch (Exception throwable) {
                recordPerformance("async-file:write:" + safeTaskName(taskName), 0L, false);
                CompletableFuture<Void> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(throwable);
                return failedFuture;
            }
        }
        return scheduler.runAsync(taskName, () -> executeWithRetry("async-file:write:" + safeTaskName(taskName), () -> {
            task.run();
            return null;
        }));
    }

    private <T> T executeWithRetry(String metricKey, Supplier<T> supplier) {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                T value = supplier.get();
                recordPerformance(metricKey, System.nanoTime() - startedAt, true);
                return value;
            } catch (Exception throwable) {
                lastFailure = throwable;
                recordPerformance(metricKey, System.nanoTime() - startedAt, false);
                if (attempt >= retryAttempts) {
                    break;
                }
                try {
                    Thread.sleep(Math.min(50L * attempt, 250L));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(exception);
                }
            }
        }
        if (lastFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException(lastFailure);
    }

    private void recordPerformance(String metricKey, long durationNanos, boolean success) {
        if (performanceMonitor != null) {
            performanceMonitor.record(metricKey, durationNanos, success);
        }
    }

    private String safeTaskName(String taskName) {
        return taskName == null || taskName.isBlank() ? "unknown" : taskName.trim();
    }
}
