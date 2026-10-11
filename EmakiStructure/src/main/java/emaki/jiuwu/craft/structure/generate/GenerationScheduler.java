package emaki.jiuwu.craft.structure.generate;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class GenerationScheduler implements Runnable {

    private static final long PUMP_PERIOD_TICKS = 1L;
    private static final int MAX_BATCH_RETRIES = 8;
    private static final String KEY_TIMEOUT = "error.generate.queue_timeout";
    private static final String KEY_CANCELLED = "error.generate.cancelled";
    private static final String KEY_FAILED = "error.generate.failed";
    private static final String DETAIL_UNREACHABLE = "区块不可达重试超限";
    private static final String DETAIL_WRITE_FAILED = "方块写入异常";

    private record QueuedBatch(GenerationJob job, ChunkBatch batch, int retries) {
    }

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final Supplier<Integer> baseBudget;
    private final Supplier<Boolean> adaptive;
    private final Supplier<Duration> queueTimeout;
    private final Object lock = new Object();
    private final Deque<QueuedBatch> queue = new ArrayDeque<>();
    private final AtomicBoolean throttleWarned = new AtomicBoolean();

    private TaskToken pumpToken;
    private volatile boolean throttledWarnActive;

    public GenerationScheduler(EmakiStructurePlugin plugin,
            ExecutionDispatcher dispatcher,
            Supplier<Integer> baseBudget,
            Supplier<Boolean> adaptive,
            Supplier<Duration> queueTimeout) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.baseBudget = baseBudget;
        this.adaptive = adaptive;
        this.queueTimeout = queueTimeout;
    }

    public void start() {
        if (pumpToken != null) {
            return;
        }
        pumpToken = dispatcher.runGlobalTimer(plugin, this, PUMP_PERIOD_TICKS, PUMP_PERIOD_TICKS);
    }

    public void submit(GenerationJob job, List<ChunkBatch> batches) {
        synchronized (lock) {
            for (ChunkBatch batch : batches) {
                queue.addLast(new QueuedBatch(job, batch, 0));
            }
        }
    }

    @Override
    public void run() {
        int budget = currentBudget();
        long timeoutNanos = queueTimeout.get().toNanos();
        synchronized (lock) {
            List<QueuedBatch> pending = new ArrayList<>(queue);
            queue.clear();
            for (QueuedBatch entry : pending) {
                if (budget <= 0) {
                    queue.addLast(entry);
                    continue;
                }
                if (entry.job().isFinished() || entry.job().abortRequested()) {
                    continue;
                }
                if (entry.job().ageNanos() > timeoutNanos) {
                    abortJob(entry.job(), KEY_TIMEOUT, entry.job().templateId());
                    continue;
                }
                TaskToken token = dispatch(entry);
                if (token == null) {
                    QueuedBatch retried = retry(entry);
                    if (retried != null) {
                        queue.addLast(retried);
                    }
                    continue;
                }
                budget -= Math.max(1, entry.batch().blockCount());
            }
        }
    }

    private TaskToken dispatch(QueuedBatch entry) {
        try {
            TaskToken token = dispatcher.runAtLocation(plugin, entry.batch().location(),
                    () -> executeBatch(entry));
            if (token == null || token == TaskToken.UNAVAILABLE) {
                return null;
            }
            return token;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("生成批次提交失败: " + exception.getMessage());
            return null;
        }
    }

    private void executeBatch(QueuedBatch entry) {
        try {
            entry.batch().writeToRegion();
            entry.batch().markFinished();
            entry.job().recordBatchFinished();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("生成批次写入失败: " + exception.getMessage());
            entry.batch().markFinished();
            entry.job().requestAbort(KEY_FAILED, DETAIL_WRITE_FAILED);
            entry.job().complete(GenerationJob.Outcome.ABORTED);
        }
    }

    private QueuedBatch retry(QueuedBatch entry) {
        int retries = entry.retries() + 1;
        if (retries > MAX_BATCH_RETRIES) {
            abortJob(entry.job(), KEY_FAILED, DETAIL_UNREACHABLE);
            return null;
        }
        return new QueuedBatch(entry.job(), entry.batch(), retries);
    }

    private void abortJob(GenerationJob job, String reasonKey, String detail) {
        job.requestAbort(reasonKey, detail);
        job.complete(GenerationJob.Outcome.ABORTED);
    }

    private int currentBudget() {
        int budget = baseBudget.get();
        boolean enabled = adaptive.get();
        if (!enabled) {
            throttleWarned.set(false);
            return Math.max(1, budget);
        }
        double tps = currentTps();
        int effective = AdaptiveThrottle.effectiveBudget(budget, true, tps);
        throttledWarnActive = AdaptiveThrottle.shouldWarn(true, tps);
        if (throttledWarnActive && throttleWarned.compareAndSet(false, true)) {
            plugin.getLogger().warning("TPS 低于阈值，生成写入限额已下调至 " + effective + "/" + budget);
        }
        if (!throttledWarnActive) {
            throttleWarned.set(false);
        }
        return Math.max(1, effective);
    }

    private double currentTps() {
        double[] tps = Bukkit.getTPS();
        if (tps == null || tps.length == 0) {
            return 20.0d;
        }
        return Math.min(20.0d, tps[0]);
    }

    public boolean throttledWarnActive() {
        return throttledWarnActive;
    }

    public void cancelAll(String reasonKey) {
        List<GenerationJob> jobs = new ArrayList<>();
        synchronized (lock) {
            for (QueuedBatch entry : queue) {
                if (!entry.job().isFinished() && !jobs.contains(entry.job())) {
                    jobs.add(entry.job());
                }
            }
            queue.clear();
        }
        for (GenerationJob job : jobs) {
            job.requestAbort(reasonKey, job.templateId());
            job.complete(GenerationJob.Outcome.CANCELLED);
        }
    }

    public int queuedBatchCount() {
        synchronized (lock) {
            return queue.size();
        }
    }

    public void close() {
        cancelAll(KEY_CANCELLED);
        if (pumpToken != null) {
            pumpToken.cancel();
            pumpToken = null;
        }
    }
}
