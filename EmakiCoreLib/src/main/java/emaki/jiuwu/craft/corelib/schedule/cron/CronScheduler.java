package emaki.jiuwu.craft.corelib.schedule.cron;

import org.bukkit.plugin.Plugin;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class CronScheduler {

    private final List<DefaultHandle> handles = new ArrayList<>();

    public CronTaskHandle schedule(Plugin plugin, String cronExpression, Runnable task) {
        return schedule(plugin, cronExpression, 0, task);
    }

    public CronTaskHandle schedule(Plugin plugin, String cronExpression, int maxExecutions, Runnable task) {
        CronExpression expr = CronExpression.parse(cronExpression);
        DefaultHandle handle = new DefaultHandle(maxExecutions);
        handles.add(handle);
        scheduleNext(plugin, expr, task, handle);
        return handle;
    }

    public void cancelAll() {
        for (DefaultHandle h : handles) {
            h.cancel();
        }
        handles.clear();
    }

    private void scheduleNext(Plugin plugin, CronExpression expr, Runnable task, DefaultHandle handle) {
        if (handle.isCancelled()) return;

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime next = expr.nextExecution(now);
        if (next == null) {
            handle.markDone();
            return;
        }

        long delayMillis = java.time.Duration.between(now, next).toMillis();
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
                handle.markDone();
                return;
            }
            scheduleNext(plugin, expr, task, handle);
        }, delayTicks);
    }

    private static final class DefaultHandle implements CronTaskHandle {

        private final AtomicInteger remaining;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        DefaultHandle(int maxExecutions) {
            this.remaining = new AtomicInteger(maxExecutions <= 0 ? -1 : maxExecutions);
        }

        int decrementAndGet() {
            int val = remaining.get();
            if (val < 0) return val;
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
