package emaki.jiuwu.craft.corelib.schedule.cron;

import java.time.Duration;
import org.bukkit.plugin.Plugin;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public final class CronScheduler {

    private final List<DefaultHandle> handles = new ArrayList<>();

    public CronTaskHandle schedule(Plugin plugin, String cronExpression, Runnable task) {
        return schedule(plugin, cronExpression, 0, task);
    }

    public CronTaskHandle schedule(Plugin plugin, String cronExpression, int maxExecutions, Runnable task) {
        CronExpression expr = CronExpression.parse(cronExpression);
        DefaultHandle handle = new DefaultHandle(this, maxExecutions);
        synchronized (handles) {
            handles.add(handle);
        }
        scheduleNext(plugin, expr, task, handle);
        return handle;
    }

    public void cancelAll() {
        List<DefaultHandle> snapshot;
        synchronized (handles) {
            snapshot = List.copyOf(handles);
            handles.clear();
        }
        for (DefaultHandle h : snapshot) {
            h.cancel();
        }
    }

    private void forget(DefaultHandle handle) {
        synchronized (handles) {
            handles.remove(handle);
        }
    }

    private void scheduleNext(Plugin plugin, CronExpression expr, Runnable task, DefaultHandle handle) {
        if (handle.isCancelled()) return;

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime next = expr.nextExecution(now);
        if (next == null) {
            handle.markDone();
            return;
        }

        long delayMillis = Duration.between(now, next).toMillis();
        long delayTicks = Math.max(1L, delayMillis / 50L);
        int generation = handle.openGeneration();

        ScheduledTask scheduledTask = plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, ignored -> {
            if (!handle.acceptGeneration(generation)) return;

            try {
                task.run();
            } catch (Throwable t) {
                plugin.getLogger().warning("[scheduler] 任务抛出异常: " + t.getMessage());
            }

            int remaining = handle.decrementAndGet();
            if (remaining == 0) {
                handle.markDone();
                return;
            }
            scheduleNext(plugin, expr, task, handle);
        }, delayTicks);
        handle.attach(scheduledTask, generation);
    }

    private static final class DefaultHandle implements CronTaskHandle {

        private final CronScheduler owner;
        private final AtomicInteger remaining;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicInteger generation = new AtomicInteger();
        private ScheduledTask scheduledTask;

        DefaultHandle(CronScheduler owner, int maxExecutions) {
            this.owner = owner;
            this.remaining = new AtomicInteger(maxExecutions <= 0 ? -1 : maxExecutions);
        }

        int openGeneration() {
            return generation.incrementAndGet();
        }

        boolean acceptGeneration(int expected) {
            return !cancelled.get() && generation.get() == expected;
        }

        synchronized void attach(ScheduledTask task, int expected) {
            if (task == null) return;
            if (cancelled.get() || generation.get() != expected) {
                task.cancel();
                return;
            }
            scheduledTask = task;
        }

        int decrementAndGet() {
            int val = remaining.get();
            if (val < 0) return val;
            return remaining.decrementAndGet();
        }

        void markDone() {
            cease();
            owner.forget(this);
        }

        private void cease() {
            cancelled.set(true);
            generation.incrementAndGet();
            ScheduledTask pending;
            synchronized (this) {
                pending = scheduledTask;
                scheduledTask = null;
            }
            if (pending != null) {
                pending.cancel();
            }
        }

        @Override
        public void cancel() {
            cease();
            owner.forget(this);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
