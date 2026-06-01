package emaki.jiuwu.craft.corelib.async;

import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Folia-compatible scheduler adapter that provides a unified API for both Spigot and Folia.
 * This class automatically detects the server type and uses the appropriate scheduler.
 */
public final class FoliaSchedulerAdapter {

    private static final boolean IS_FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    private FoliaSchedulerAdapter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks if the server is running Folia.
     *
     * @return true if Folia is detected, false otherwise
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * Runs a task on the main/global thread.
     * On Folia, this uses the global region scheduler.
     * On Spigot, this uses the standard Bukkit scheduler.
     *
     * @param plugin the plugin scheduling the task
     * @param task   the task to run
     * @return a task handle (may be null if task ran immediately or plugin is disabled)
     */
    public static Object runTask(Plugin plugin, Runnable task) {
        if (plugin == null || task == null) {
            return null;
        }
        if (!plugin.isEnabled()) {
            task.run();
            return null;
        }
        if (IS_FOLIA) {
            return Bukkit.getGlobalRegionScheduler().run(plugin, _ -> task.run());
        } else {
            if (Bukkit.isPrimaryThread()) {
                task.run();
                return null;
            } else {
                return plugin.getServer().getScheduler().runTask(plugin, task);
            }
        }
    }

    /**
     * Runs a task on the main/global thread after a delay.
     * On Folia, this uses the global region scheduler.
     * On Spigot, this uses the standard Bukkit scheduler.
     *
     * @param plugin     the plugin scheduling the task
     * @param task       the task to run
     * @param delayTicks the delay in ticks
     */
    public static void runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (plugin == null || task == null) {
            return;
        }
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, _ -> task.run(), delayTicks);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Runs a repeating task on the main/global thread.
     * On Folia, this uses the global region scheduler.
     * On Spigot, this uses the standard Bukkit scheduler.
     *
     * @param plugin       the plugin scheduling the task
     * @param task         the task to run
     * @param delayTicks   the initial delay in ticks
     * @param periodTicks  the period between executions in ticks
     * @return a task handle that can be used to cancel the task
     */
    public static Object runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (plugin == null || task == null) {
            return null;
        }
        if (!plugin.isEnabled()) {
            return null;
        }
        if (IS_FOLIA) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, _ -> task.run(), delayTicks, periodTicks);
        } else {
            return plugin.getServer().getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    /**
     * Runs a task on the entity's scheduler (Folia) or main thread (Spigot).
     * On Folia, this uses the entity scheduler.
     * On Spigot, this uses the standard Bukkit scheduler.
     *
     * @param plugin the plugin scheduling the task
     * @param entity the entity to schedule the task for
     * @param task   the task to run
     */
    public static void runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (plugin == null || entity == null || task == null) {
            return;
        }
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }
        if (IS_FOLIA) {
            entity.getScheduler().run(plugin, _ -> task.run(), null);
        } else {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                plugin.getServer().getScheduler().runTask(plugin, task);
            }
        }
    }

    /**
     * Runs a task on the entity's scheduler after a delay (Folia) or main thread (Spigot).
     * On Folia, this uses the entity scheduler.
     * On Spigot, this uses the standard Bukkit scheduler.
     *
     * @param plugin     the plugin scheduling the task
     * @param entity     the entity to schedule the task for
     * @param task       the task to run
     * @param delayTicks the delay in ticks
     */
    public static void runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (plugin == null || entity == null || task == null) {
            return;
        }
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }
        if (IS_FOLIA) {
            entity.getScheduler().runDelayed(plugin, _ -> task.run(), null, delayTicks);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Runs a task at a specific location (Folia) or main thread (Spigot).
     * On Folia, this uses the region scheduler for the location.
     * On Spigot, this uses the standard Bukkit scheduler.
     *
     * @param plugin   the plugin scheduling the task
     * @param location the location to schedule the task for
     * @param task     the task to run
     */
    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (plugin == null || location == null || task == null) {
            return;
        }
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().run(plugin, location, _ -> task.run());
        } else {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                plugin.getServer().getScheduler().runTask(plugin, task);
            }
        }
    }

    /**
     * Runs a task at a specific location after a delay (Folia) or main thread (Spigot).
     * On Folia, this uses the region scheduler for the location.
     * On Spigot, this uses the standard Bukkit scheduler.
     *
     * @param plugin     the plugin scheduling the task
     * @param location   the location to schedule the task for
     * @param task       the task to run
     * @param delayTicks the delay in ticks
     */
    public static void runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (plugin == null || location == null || task == null) {
            return;
        }
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, _ -> task.run(), delayTicks);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Runs an asynchronous task.
     * This method works the same on both Folia and Spigot.
     *
     * @param plugin the plugin scheduling the task
     * @param task   the task to run
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        if (plugin == null || task == null) {
            return;
        }
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, _ -> task.run());
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Runs an asynchronous task after a delay.
     * This method works the same on both Folia and Spigot.
     *
     * @param plugin the plugin scheduling the task
     * @param task   the task to run
     * @param delay  the delay
     * @param unit   the time unit for the delay
     */
    public static void runAsyncLater(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        if (plugin == null || task == null) {
            return;
        }
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, _ -> task.run(), delay, unit);
        } else {
            long ticks = unit.toMillis(delay) / 50L;
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, task, Math.max(1L, ticks));
        }
    }

    /**
     * Cancels a task.
     * This method handles both Folia and Spigot task types.
     *
     * @param task the task to cancel (can be BukkitTask or Folia ScheduledTask)
     */
    public static void cancelTask(Object task) {
        if (task == null) {
            return;
        }
        if (IS_FOLIA) {
            if (task instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduledTask) {
                scheduledTask.cancel();
            }
        } else {
            if (task instanceof org.bukkit.scheduler.BukkitTask bukkitTask) {
                bukkitTask.cancel();
            }
        }
    }
}
