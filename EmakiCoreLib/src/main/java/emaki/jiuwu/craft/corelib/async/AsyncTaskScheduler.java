package emaki.jiuwu.craft.corelib.async;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
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
    private static final int DEFAULT_MAX_QUEUED_TASKS = Integer.getInteger(
            "emaki.async.maxQueuedTasks", 10_000);

    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService timeoutExecutor;
    private final Executor syncExecutor;
    private final long defaultTimeoutMillis;
    private final int maxQueuedTasks;
    private final PerformanceMonitor performanceMonitor;
    private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong timedOut = new AtomicLong();
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentMap<CompletableFuture<?>, ScheduledFuture<?>> delayedTasks = new ConcurrentHashMap<>();

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
        this(executor, timeoutExecutor, syncExecutor, defaultTimeoutMillis,
                performanceMonitor, DEFAULT_MAX_QUEUED_TASKS);
    }

    public AsyncTaskScheduler(ThreadPoolExecutor executor,
            ScheduledExecutorService timeoutExecutor,
            Executor syncExecutor,
            long defaultTimeoutMillis,
            PerformanceMonitor performanceMonitor,
            int maxQueuedTasks) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.timeoutExecutor = Objects.requireNonNull(timeoutExecutor, "timeoutExecutor");
        this.syncExecutor = syncExecutor == null ? Runnable::run : syncExecutor;
        this.defaultTimeoutMillis = defaultTimeoutMillis <= 0L ? DEFAULT_TIMEOUT_MILLIS : defaultTimeoutMillis;
        this.maxQueuedTasks = Math.max(0, maxQueuedTasks);
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
                throw new RejectedExecutionException("Plugin is disabled; sync dispatch rejected");
            }
            FoliaSchedulerAdapter.runTask(plugin, runnable);
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
                throw new RejectedExecutionException("Plugin is disabled; sync dispatch rejected");
            }
            FoliaSchedulerAdapter.runTask(plugin, runnable);
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
        if (!acceptingTasks.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("Async scheduler is shutting down"));
        }
        if (asyncQueueFull()) {
            failed.incrementAndGet();
            return CompletableFuture.failedFuture(new RejectedExecutionException(
                    "Async task queue is full for task: " + safeTaskName(taskName)));
        }
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
        future.whenComplete((_, throwable) -> activeTasks.decrementAndGet());
        try {
            executor.execute(task);
        } catch (Throwable throwable) {
            failed.incrementAndGet();
            future.completeExceptionally(throwable);
            return future;
        }
        if (timeoutMillis <= 0L) {
            return future;
        }
        ScheduledFuture<?> timeout;
        try {
            timeout = timeoutExecutor.schedule(() -> {
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
        } catch (Throwable throwable) {
            executor.remove(task);
            task.cancel();
            failed.incrementAndGet();
            future.completeExceptionally(throwable);
            return future;
        }
        future.whenComplete((_, throwable) -> timeout.cancel(false));
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

    public <T> CompletableFuture<T> schedule(String taskName,
            long delayMillis,
            long timeoutMillis,
            TaskPriority priority,
            Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        if (!acceptingTasks.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("Async scheduler is shutting down"));
        }
        if (delayedQueueFull()) {
            failed.incrementAndGet();
            return CompletableFuture.failedFuture(new RejectedExecutionException(
                    "Async delayed task queue is full for task: " + safeTaskName(taskName)));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            ScheduledFuture<?> scheduled = timeoutExecutor.schedule(() -> {
                delayedTasks.remove(result);
                if (!acceptingTasks.get()) {
                    result.completeExceptionally(new RejectedExecutionException("Async scheduler is shutting down"));
                    return;
                }
                supplyAsync(taskName, priority, timeoutMillis, supplier)
                        .whenComplete((value, throwable) -> {
                            if (throwable == null) {
                                result.complete(value);
                            } else {
                                result.completeExceptionally(throwable);
                            }
                        });
            }, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
            delayedTasks.put(result, scheduled);
            if (result.isDone()) {
                delayedTasks.remove(result, scheduled);
            }
        } catch (Throwable throwable) {
            result.completeExceptionally(throwable);
        }
        return result;
    }

    private boolean asyncQueueFull() {
        return executor.getActiveCount() >= executor.getMaximumPoolSize()
                && executor.getQueue().size() >= maxQueuedTasks;
    }

    private boolean delayedQueueFull() {
        return delayedTasks.size() >= maxQueuedTasks;
    }

    public <T> CompletableFuture<T> callSync(String taskName, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        if (!acceptingTasks.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("Async scheduler is shutting down"));
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
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
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
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
        shutdownGracefully(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public boolean shutdownGracefully(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        acceptingTasks.set(false);
        RejectedExecutionException shutdownFailure = new RejectedExecutionException("Async scheduler is shutting down");
        delayedTasks.forEach((future, scheduled) -> {
            scheduled.cancel(false);
            future.completeExceptionally(shutdownFailure);
        });
        delayedTasks.clear();
        if (!executor.isShutdown()) {
            int poolSize = executor.getCorePoolSize();
            for (int i = 0; i < poolSize; i++) {
                try {
                    executor.execute(new ComparableRunnable(ExpressionEngine::clearThreadLocalCache));
                } catch (RejectedExecutionException | ClassCastException _) {
                    break;
                }
            }
        }
        long timeoutNanos = Math.max(1L, unit.toNanos(timeout));
        long deadline = System.nanoTime() + timeoutNanos;
        executor.shutdown();
        try {
            boolean executorTerminated = executor.awaitTermination(timeoutNanos, TimeUnit.NANOSECONDS);
            if (!executorTerminated) {
                executor.shutdownNow();
            }
            timeoutExecutor.shutdownNow();
            long remainingNanos = Math.max(1L, deadline - System.nanoTime());
            boolean timeoutExecutorTerminated = timeoutExecutor.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS);
            return executorTerminated && timeoutExecutorTerminated;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            timeoutExecutor.shutdownNow();
            return false;
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class ComparableRunnable implements Runnable, Comparable {

        private final Runnable delegate;

        private ComparableRunnable(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            delegate.run();
        }

        @Override
        public int compareTo(Object other) {
            if (other instanceof ComparableRunnable) {
                return 0;
            }
            return 1;
        }
    }
}
