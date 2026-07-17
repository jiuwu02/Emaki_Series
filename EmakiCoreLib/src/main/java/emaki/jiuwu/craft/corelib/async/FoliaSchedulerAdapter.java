package emaki.jiuwu.craft.corelib.async;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.runtime.CapabilityProbe;

/**
 * Unified scheduler facade for Bukkit, Paper, and Folia runtimes.
 *
 * <p>Public signatures deliberately expose only Bukkit types and Emaki's own
 * {@link TaskHandle}. Paper/Folia-specific scheduler types stay inside the
 * CoreLib compatibility layer.
 */
public final class FoliaSchedulerAdapter {

    private static volatile SchedulerCompat cachedCompat;
    private static volatile CapabilityProbe cachedCapabilities;
    private static volatile Server cachedServer;

    private FoliaSchedulerAdapter() {
    }

    public static boolean isFolia() {
        return capabilities(Bukkit.getServer()).folia();
    }

    public static CapabilityProbe capabilities(Plugin plugin) {
        return capabilities(plugin == null ? null : plugin.getServer());
    }

    public static TaskHandle runTask(Plugin plugin, Runnable task) {
        return compat(plugin).runTask(plugin, task);
    }

    public static TaskHandle runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        return compat(plugin).runTaskLater(plugin, task, delayTicks);
    }

    public static TaskHandle runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return compat(plugin).runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    public static TaskHandle runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        return compat(plugin).runEntityTask(plugin, entity, task);
    }

    public static TaskHandle runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        return compat(plugin).runEntityTaskLater(plugin, entity, task, delayTicks);
    }

    public static TaskHandle runEntityTaskLater(
            Plugin plugin,
            Entity entity,
            Runnable task,
            Runnable retired,
            long delayTicks) {
        return compat(plugin).runEntityTaskLater(plugin, entity, task, retired, delayTicks);
    }

    public static TaskHandle runAtLocation(Plugin plugin, Location location, Runnable task) {
        return compat(plugin).runAtLocation(plugin, location, task);
    }

    public static TaskHandle runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        return compat(plugin).runAtLocationLater(plugin, location, task, delayTicks);
    }

    public static TaskHandle runAsync(Plugin plugin, Runnable task) {
        return compat(plugin).runAsync(plugin, task);
    }

    public static TaskHandle runAsyncLater(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        return compat(plugin).runAsyncLater(plugin, task, delay, unit);
    }

    public static void cancelTask(TaskHandle task) {
        if (task != null) {
            task.cancel();
        }
    }

    public static boolean isTaskCancelled(TaskHandle task) {
        return task == null || task.isCancelled();
    }

    private static SchedulerCompat compat(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (!plugin.isEnabled()) {
            throw new RejectedExecutionException("Plugin is disabled; scheduler task rejected: " + plugin.getName());
        }
        Server server = plugin.getServer();
        if (server == null) {
            throw new RejectedExecutionException("Server is unavailable; scheduler task rejected: " + plugin.getName());
        }
        SchedulerCompat resolved = cachedCompat;
        if (resolved != null && cachedServer == server) {
            return resolved;
        }
        synchronized (FoliaSchedulerAdapter.class) {
            if (cachedCompat == null || cachedServer != server) {
                cachedCapabilities = CapabilityProbe.detect(server);
                cachedCompat = resolveCompat(server, cachedCapabilities);
                cachedServer = server;
            }
            return cachedCompat;
        }
    }

    private static CapabilityProbe capabilities(Server server) {
        CapabilityProbe resolved = cachedCapabilities;
        if (resolved != null && cachedServer == server) {
            return resolved;
        }
        synchronized (FoliaSchedulerAdapter.class) {
            if (cachedCapabilities == null || cachedServer != server) {
                cachedCapabilities = CapabilityProbe.detect(server);
                cachedCompat = server == null ? null : resolveCompat(server, cachedCapabilities);
                cachedServer = server;
            }
            return cachedCapabilities;
        }
    }

    private static SchedulerCompat resolveCompat(Server server, CapabilityProbe capabilities) {
        if (!capabilities.folia()) {
            return new BukkitSchedulerCompat();
        }
        try {
            SchedulerCompat foliaCompat = FoliaSchedulerCompat.createIfSupported(server, true);
            if (foliaCompat == null) {
                throw new IllegalStateException("Folia scheduler capabilities were detected but could not be linked");
            }
            return foliaCompat;
        } catch (RuntimeException | LinkageError exception) {
            throw new IllegalStateException("Failed to initialize the Folia scheduler boundary", exception);
        }
    }
}
