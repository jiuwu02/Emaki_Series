package emaki.jiuwu.craft.corelib.schedule.cron;

import org.bukkit.plugin.Plugin;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 Cron 表达式的任务调度器。
 *
 * <p>使用 Paper 全局区域调度器（GlobalRegionScheduler）实现单次延迟触发 + 自动重调度。
 * 每个调度器实例独立管理其注册的任务，调用 {@link #cancelAll()} 可一次性取消所有任务。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * CronScheduler scheduler = new CronScheduler();
 *
 * // 无限循环，每天 19:00 执行
 * CronTaskHandle handle = scheduler.schedule(plugin, "0 0 19 * * ?", () -> {
 *     // 任务逻辑
 * });
 *
 * // 最多执行 3 次
 * CronTaskHandle handle2 = scheduler.schedule(plugin, "0 0/15 * * * *", 3, () -> {
 *     // 任务逻辑
 * });
 *
 * // 插件卸载/重载时：
 * scheduler.cancelAll();
 * }</pre>
 */
public final class CronScheduler {

    private final List<DefaultHandle> handles = new ArrayList<>();

    /**
     * 注册一个无限循环的 Cron 任务。
     *
     * @param plugin         注册该任务的插件（用于 Bukkit 调度）
     * @param cronExpression 6 段 Quartz Cron 表达式
     * @param task           任务体
     * @return 任务句柄，可用于提前取消
     * @throws CronParseException 表达式解析失败时抛出
     */
    public CronTaskHandle schedule(Plugin plugin, String cronExpression, Runnable task) {
        return schedule(plugin, cronExpression, 0, task);
    }

    /**
     * 注册一个有最大执行次数限制的 Cron 任务。
     *
     * @param plugin          注册该任务的插件
     * @param cronExpression  6 段 Quartz Cron 表达式
     * @param maxExecutions   最大执行次数（{@code <= 0} 表示无限循环）
     * @param task            任务体
     * @return 任务句柄，可用于提前取消
     * @throws CronParseException 表达式解析失败时抛出
     */
    public CronTaskHandle schedule(Plugin plugin, String cronExpression, int maxExecutions, Runnable task) {
        CronExpression expr = CronExpression.parse(cronExpression);
        DefaultHandle handle = new DefaultHandle(maxExecutions);
        handles.add(handle);
        scheduleNext(plugin, expr, task, handle);
        return handle;
    }

    /**
     * 取消该调度器注册的所有任务。
     */
    public void cancelAll() {
        for (DefaultHandle h : handles) {
            h.cancel();
        }
        handles.clear();
    }

    // ─── 内部调度逻辑 ─────────────────────────────────────────────────────────

    private void scheduleNext(Plugin plugin, CronExpression expr, Runnable task, DefaultHandle handle) {
        if (handle.isCancelled()) return;

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime next = expr.nextExecution(now);
        if (next == null) {
            // 4年内找不到下次执行时间，视为任务结束
            handle.markDone();
            return;
        }

        long delayMillis = java.time.Duration.between(now, next).toMillis();
        // 最小保证 50ms（1 tick），避免零或负数延迟
        long delayTicks = Math.max(1L, delayMillis / 50L);

        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> {
            if (handle.isCancelled()) return;

            try {
                task.run();
            } catch (Throwable t) {
                plugin.getLogger().warning("[CronScheduler] Task threw exception: " + t.getMessage());
            }

            int remaining = handle.decrementAndGet();
            if (remaining == 0) {
                // maxExecutions 耗尽
                handle.markDone();
                return;
            }
            // 重调度下一次
            scheduleNext(plugin, expr, task, handle);
        }, delayTicks);
    }

    // ─── 句柄实现 ─────────────────────────────────────────────────────────────

    private static final class DefaultHandle implements CronTaskHandle {

        /** 剩余执行次数：-1 = 无限，0 = 已结束，>0 = 有限剩余 */
        private final AtomicInteger remaining;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        DefaultHandle(int maxExecutions) {
            this.remaining = new AtomicInteger(maxExecutions <= 0 ? -1 : maxExecutions);
        }

        /**
         * 减少剩余次数（仅在 maxExecutions > 0 时有效）。
         * 返回更新后的值；-1 表示无限循环，0 表示耗尽，>0 表示仍有剩余。
         */
        int decrementAndGet() {
            int val = remaining.get();
            if (val < 0) return val; // 无限循环，不减
            return remaining.decrementAndGet();
        }

        void markDone() {
            cancelled.set(true);
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
