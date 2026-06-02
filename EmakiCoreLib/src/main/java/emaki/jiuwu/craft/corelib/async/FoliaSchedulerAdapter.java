package emaki.jiuwu.craft.corelib.async;

import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Unified scheduler facade for Bukkit, Paper, and Folia runtimes.
 *
 * <p>Public signatures deliberately expose only Bukkit types and Emaki's own
 * {@link TaskHandle}. Paper/Folia-specific scheduler types stay inside the
 * CoreLib compatibility layer.
 */
public final class FoliaSchedulerAdapter {

    private static volatile SchedulerCompat compat;

    private FoliaSchedulerAdapter() {
    }

    public static boolean isFolia() {
        return compat().isFolia();
    }

    public static TaskHandle runTask(Plugin plugin, Runnable task) {
        return compat().runTask(plugin, task);
    }

    public static TaskHandle runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        return compat().runTaskLater(plugin, task, delayTicks);
    }

    public static TaskHandle runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return compat().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    public static TaskHandle runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        return compat().runEntityTask(plugin, entity, task);
    }

    public static TaskHandle runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        return compat().runEntityTaskLater(plugin, entity, task, delayTicks);
    }

    public static TaskHandle runAtLocation(Plugin plugin, Location location, Runnable task) {
        return compat().runAtLocation(plugin, location, task);
    }

    public static TaskHandle runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        return compat().runAtLocationLater(plugin, location, task, delayTicks);
    }

    public static TaskHandle runAsync(Plugin plugin, Runnable task) {
        return compat().runAsync(plugin, task);
    }

    public static TaskHandle runAsyncLater(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        return compat().runAsyncLater(plugin, task, delay, unit);
    }

    public static void cancelTask(TaskHandle task) {
        if (task != null) {
            task.cancel();
        }
    }

    public static boolean isTaskCancelled(TaskHandle task) {
        return task == null || task.isCancelled();
    }

    private static SchedulerCompat compat() {
        SchedulerCompat resolved = compat;
        if (resolved != null) {
            return resolved;
        }
        Server server = Bukkit.getServer();
        if (server == null) {
            return new BukkitSchedulerCompat();
        }
        synchronized (FoliaSchedulerAdapter.class) {
            if (compat == null) {
                compat = resolveCompat(server);
            }
            return compat;
        }
    }

    private static SchedulerCompat resolveCompat(Server server) {
        SchedulerCompat foliaCompat = FoliaSchedulerCompat.createIfSupported(server, isFoliaPlatform(server));
        return foliaCompat != null ? foliaCompat : new BukkitSchedulerCompat();
    }

    private static boolean isFoliaPlatform(Server server) {
        if (server == null) {
            return false;
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, server.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
