package emaki.jiuwu.craft.corelib.async;

import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

final class BukkitSchedulerCompat implements SchedulerCompat {

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public TaskHandle runTask(Plugin plugin, Runnable task) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        return wrap(plugin.getServer().getScheduler().runTask(plugin, task));
    }

    @Override
    public TaskHandle runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        return wrap(plugin.getServer().getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks)));
    }

    @Override
    public TaskHandle runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        return wrap(plugin.getServer().getScheduler().runTaskTimer(plugin, task, Math.max(1L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public TaskHandle runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        return runTask(plugin, task);
    }

    @Override
    public TaskHandle runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        return runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public TaskHandle runAtLocation(Plugin plugin, Location location, Runnable task) {
        return runTask(plugin, task);
    }

    @Override
    public TaskHandle runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        return runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public TaskHandle runAsync(Plugin plugin, Runnable task) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        return wrap(plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task));
    }

    @Override
    public TaskHandle runAsyncLater(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        TimeUnit safeUnit = unit == null ? TimeUnit.MILLISECONDS : unit;
        long safeDelay = Math.max(1L, delay);
        long ticks = Math.max(1L, safeUnit.toMillis(safeDelay) / 50L);
        return wrap(plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, task, ticks));
    }

    private boolean canSchedule(Plugin plugin, Runnable task) {
        return plugin != null && task != null && plugin.isEnabled();
    }

    private TaskHandle wrap(org.bukkit.scheduler.BukkitTask task) {
        return task == null ? null : new BukkitTaskHandle(task);
    }
}
