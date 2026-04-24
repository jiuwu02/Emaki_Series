package emaki.jiuwu.craft.corelib.async;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;

public final class AsyncTaskScheduler implements AutoCloseable {

    public enum TaskPriority {
        HIGH(0),
        NORMAL(1),
        LOW(2);

        private final int sortOrder;

        TaskPriority(int sortOrder) {
            this.sortOrder = sortOrder;
        }

        int sortOrder() {
            return sortOrder;
        }
    }

    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService timeoutExecutor;
    private final Executor syncExecutor;
    private final long defaultTimeoutMillis;
    private final PerformanceMonitor performanceMonitor;
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong timedOut = new AtomicLong();
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final AtomicLong sequence = new AtomicLong();

    public AsyncTaskScheduler(Executor syncExecutor,
            int threadCount,
            long defaultTimeoutMillis,
            String threadPrefix) {
        this(syncExecutor, threadCount, defaultTimeoutMillis, threadPrefix, null);
    }

    public AsyncTaskScheduler(Executor syncExecutor,
            int threadCount,
            long defaultTimeoutMillis,
            String threadPrefix,
            PerformanceMonitor performanceMonitor) {
        this(
                new ThreadPoolExecutor(
                        Math.max(1, threadCount),
                        Math.max(1, threadCount),
                        30L,
                        TimeUnit.SECONDS,
                        new PriorityBlockingQueue<>(),
                        new NamedThreadFactory(threadPrefix)
                ),
                Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(threadPrefix + "-timeout")),
                syncExecutor,
                defaultTimeoutMillis,
                performanceMonitor
        );
    }

    public AsyncTaskScheduler(Executor syncExecutor,
            int threadCount,
            long defaultTimeoutMillis,
            String threadPrefix,
            PerformanceMonitor performanceMonitor,
            boolean useVirtualThreads) {
        this(
                new ThreadPoolExecutor(
                        Math.max(1, threadCount),
                        Math.max(1, threadCount),
                        30L,
                        TimeUnit.SECONDS,
                        new PriorityBlockingQueue<>(),
                        new NamedThreadFactory(threadPrefix, useVirtualThreads)
                ),
                Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(threadPrefix + "-timeout")),
                syncExecutor,
                defaultTimeoutMillis,
                performanceMonitor
        );
    }

    public AsyncTaskScheduler(ThreadPoolExecutor executor,
            ScheduledExecutorService timeoutExecutor,
            Executor syncExecutor,
            long defaultTimeoutMillis,
            PerformanceMonitor performanceMonitor) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.timeoutExecutor = Objects.requireNonNull(timeoutExecutor, "timeoutExecutor");
        this.syncExecutor = syncExecutor == null ? Runnable::run : syncExecutor;
        this.defaultTimeoutMillis = defaultTimeoutMillis <= 0L ? DEFAULT_TIMEOUT_MILLIS : defaultTimeoutMillis;
        this.performanceMonitor = performanceMonitor;
        this.executor.allowCoreThreadTimeOut(false);
    }

    public static AsyncTaskScheduler forPlugin(Plugin plugin, String threadPrefix) {
        return forPlugin(plugin, threadPrefix, null);
    }

    public static AsyncTaskScheduler forPlugin(Plugin plugin, String threadPrefix, PerformanceMonitor performanceMonitor) {
        Executor sync = runnable -> {
            if (runnable == null) {
                return;
            }
            if (plugin == null || !plugin.isEnabled()) {
                runnable.run();
                return;
            }
            if (Bukkit.isPrimaryThread()) {
                runnable.run();
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, runnable);
        };
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        return new AsyncTaskScheduler(sync, threads, DEFAULT_TIMEOUT_MILLIS, threadPrefix, performanceMonitor);
    }

    public static AsyncTaskScheduler forPluginVirtual(Plugin plugin, String threadPrefix, PerformanceMonitor performanceMonitor) {
        Executor sync = runnable -> {
            if (runnable == null) {
                return;
            }
            if (plugin == null || !plugin.isEnabled()) {
                runnable.run();
                return;
            }
            if (Bukkit.isPrimaryThread()) {
                runnable.run();
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, runnable);
        };
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        return new AsyncTaskScheduler(sync, threads, DEFAULT_TIMEOUT_MILLIS, threadPrefix, performanceMonitor, true);
    }

    public <T> CompletableFuture<T> supplyAsync(String taskName, Supplier<T> supplier) {
        return supplyAsync(taskName, TaskPriority.NORMAL, defaultTimeoutMillis, supplier);
    }

    public <T> CompletableFuture<T> supplyAsync(String taskName, long timeoutMillis, Supplier<T> supplier) {
        return supplyAsync(taskName, TaskPriority.NORMAL, timeoutMillis, supplier);
    }

    public <T> CompletableFuture<T> supplyAsync(String taskName,
            TaskPriority priority,
            long timeoutMillis,
            Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        submitted.incrementAndGet();
        activeTasks.incrementAndGet();
        CompletableFuture<T> future = new CompletableFuture<>();
        PrioritizedTask<T> task = new PrioritizedTask<>(
                taskName,
                priority == null ? TaskPriority.NORMAL : priority,
                sequence.incrementAndGet(),
                supplier,
                future
        );
        executor.execute(task);
        ScheduledFuture<?> timeout = timeoutMillis <= 0L ? null : timeoutExecutor.schedule(() -> {
            TimeoutException exception = new TimeoutException("Async task timed out: " + taskName);
            if (future.isDone()) {
                return;
            }
            timedOut.incrementAndGet();
            if (!future.completeExceptionally(exception)) {
                timedOut.decrementAndGet();
                return;
            }
            executor.remove(task);
            task.cancel();
        }, timeoutMillis, TimeUnit.MILLISECONDS);
        if (timeout != null) {
            future.whenComplete((ignored, throwable) -> timeout.cancel(false));
        }
        future.whenComplete((ignored, throwable) -> activeTasks.decrementAndGet());
        return future;
    }

    public CompletableFuture<Void> runAsync(String taskName, Runnable task) {
        return runAsync(taskName, TaskPriority.NORMAL, task);
    }

    public CompletableFuture<Void> runAsync(String taskName, TaskPriority priority, Runnable task) {
        Objects.requireNonNull(task, "task");
        return supplyAsync(taskName, priority, defaultTimeoutMillis, () -> {
            task.run();
            return null;
        });
    }

    public <T> CompletableFuture<T> callSync(String taskName, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        CompletableFuture<T> future = new CompletableFuture<>();
        syncExecutor.execute(() -> {
            long startedAt = System.nanoTime();
            try {
                T value = supplier.get();
                recordPerformance("async-task:sync:" + safeTaskName(taskName), System.nanoTime() - startedAt, true);
                future.complete(value);
            } catch (Throwable throwable) {
                recordPerformance("async-task:sync:" + safeTaskName(taskName), System.nanoTime() - startedAt, false);
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    public AsyncTaskSnapshot snapshot() {
        return new AsyncTaskSnapshot(
                submitted.get(),
                completed.get(),
                failed.get(),
                timedOut.get(),
                activeTasks.get(),
                executor.getQueue().size()
        );
    }

    public void shutdown(long timeoutMillis) {
        executor.shutdown();
        timeoutExecutor.shutdown();
        try {
            long waitMillis = Math.max(1L, timeoutMillis);
            if (!executor.awaitTermination(waitMillis, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
            if (!timeoutExecutor.awaitTermination(waitMillis, TimeUnit.MILLISECONDS)) {
                timeoutExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            timeoutExecutor.shutdownNow();
        }
    }

    @Override
    public void close() {
        shutdown(defaultTimeoutMillis);
    }

    private void recordPerformance(String taskName, long durationNanos, boolean success) {
        if (performanceMonitor != null) {
            performanceMonitor.record(taskName, durationNanos, success);
        }
    }

    private String safeTaskName(String taskName) {
        return taskName == null || taskName.isBlank() ? "unknown" : taskName.trim();
    }

    private final class PrioritizedTask<T> implements Runnable, Comparable<PrioritizedTask<?>> {

        private final String taskName;
        private final TaskPriority priority;
        private final long sequence;
        private final Supplier<T> supplier;
        private final CompletableFuture<T> future;
        private final AtomicReference<Thread> runner = new AtomicReference<>();
        private volatile boolean cancelled;

        private PrioritizedTask(String taskName,
                TaskPriority priority,
                long sequence,
                Supplier<T> supplier,
                CompletableFuture<T> future) {
            this.taskName = safeTaskName(taskName);
            this.priority = priority;
            this.sequence = sequence;
            this.supplier = supplier;
            this.future = future;
        }

        @Override
        public void run() {
            if (cancelled || future.isDone()) {
                return;
            }
            runner.set(Thread.currentThread());
            long startedAt = System.nanoTime();
            try {
                if (cancelled || future.isDone()) {
                    return;
                }
                T value = supplier.get();
                if (future.complete(value)) {
                    completed.incrementAndGet();
                    recordPerformance("async-task:" + taskName, System.nanoTime() - startedAt, true);
                }
            } catch (Throwable throwable) {
                if (future.completeExceptionally(throwable)) {
                    failed.incrementAndGet();
                    recordPerformance("async-task:" + taskName, System.nanoTime() - startedAt, false);
                }
            } finally {
                runner.set(null);
            }
        }

        private void cancel() {
            cancelled = true;
            Thread thread = runner.getAndSet(null);
            if (thread != null) {
                thread.interrupt();
            }
        }

        @Override
        public int compareTo(PrioritizedTask<?> other) {
            if (other == null) {
                return -1;
            }
            int priorityCompare = Integer.compare(priority.sortOrder(), other.priority.sortOrder());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Long.compare(sequence, other.sequence);
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {

        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();
        private final boolean virtual;

        private NamedThreadFactory(String prefix) {
            this(prefix, false);
        }

        private NamedThreadFactory(String prefix, boolean virtual) {
            this.prefix = prefix == null || prefix.isBlank() ? "emaki-async" : prefix;
            this.virtual = virtual;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            if (virtual) {
                return Thread.ofVirtual()
                        .name(prefix + "-vt-" + counter.incrementAndGet())
                        .unstarted(runnable);
            }
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
