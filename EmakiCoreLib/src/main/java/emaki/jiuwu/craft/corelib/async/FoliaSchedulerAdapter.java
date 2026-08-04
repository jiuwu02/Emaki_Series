package emaki.jiuwu.craft.corelib.async;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionBackendLoader;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.runtime.CapabilityProbe;

/**
 * Legacy static scheduler facade kept only for third-party source compatibility.
 *
 * <p>This class has <strong>no remaining users in this repository</strong> (verified: the only
 * matches are its own declarations). Every method already delegates to
 * {@link ExecutionDispatcher}, so it adds nothing but a second way to reach the same behaviour.
 *
 * <p>It is actively harmful to keep as a supported entry point: it exposes a static, plugin-less
 * scheduling surface that invites callers to bypass the owner-thread abstraction, and it declares
 * legacy scheduler shapes that do not exist on Folia. New code must go through
 * {@link ExecutionDispatcher} so the region/entity/location owner is explicit.
 *
 * <p>Scheduled for removal in the next major version, after one full minor cycle. Before removal,
 * re-run a source and binary usage check — it was published as part of CoreLib's public surface.
 *
 * @deprecated obtain an {@link ExecutionDispatcher} and call
 *         {@code runGlobal} / {@code runEntity} / {@code runAtLocation} / {@code runAsync} on it,
 *         which return {@link emaki.jiuwu.craft.corelib.execution.TaskHandle} and make the owning
 *         thread explicit
 */
@Deprecated(forRemoval = true)
public final class FoliaSchedulerAdapter {

    private static volatile ExecutionDispatcher cachedDispatcher;
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
        return wrap(dispatcher(plugin).runGlobal(plugin, task));
    }

    public static TaskHandle runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        return wrap(dispatcher(plugin).runGlobalLater(plugin, task, delayTicks));
    }

    public static TaskHandle runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return wrap(dispatcher(plugin).runGlobalTimer(plugin, task, delayTicks, periodTicks));
    }

    public static TaskHandle runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (entity == null || !capabilities(plugin).folia()) {
            return runTask(plugin, task);
        }
        return wrap(dispatcher(plugin).runEntity(plugin, entity, task, null));
    }

    public static TaskHandle runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        return runEntityTaskLater(plugin, entity, task, null, delayTicks);
    }

    public static TaskHandle runEntityTaskLater(
            Plugin plugin,
            Entity entity,
            Runnable task,
            Runnable retired,
            long delayTicks) {
        if (entity == null || !capabilities(plugin).folia()) {
            return runTaskLater(plugin, task, delayTicks);
        }
        return wrap(dispatcher(plugin).runEntityLater(plugin, entity, task, retired, delayTicks));
    }

    public static TaskHandle runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (location == null || !capabilities(plugin).folia()) {
            return runTask(plugin, task);
        }
        return wrap(dispatcher(plugin).runAtLocation(plugin, location, task));
    }

    public static TaskHandle runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (location == null || !capabilities(plugin).folia()) {
            return runTaskLater(plugin, task, delayTicks);
        }
        return wrap(dispatcher(plugin).runAtLocationLater(plugin, location, task, delayTicks));
    }

    public static TaskHandle runAsync(Plugin plugin, Runnable task) {
        return wrap(dispatcher(plugin).runAsync(plugin, task));
    }

    public static TaskHandle runAsyncLater(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        return wrap(dispatcher(plugin).runAsyncLater(plugin, task, delay, unit));
    }

    public static void cancelTask(TaskHandle task) {
        if (task != null) {
            task.cancel();
        }
    }

    public static boolean isTaskCancelled(TaskHandle task) {
        return task == null || task.isCancelled();
    }

    private static ExecutionDispatcher dispatcher(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (!plugin.isEnabled()) {
            throw new RejectedExecutionException("Plugin is disabled; scheduler task rejected: " + plugin.getName());
        }
        Server server = plugin.getServer();
        if (server == null) {
            throw new RejectedExecutionException("Server is unavailable; scheduler task rejected: " + plugin.getName());
        }
        ExecutionDispatcher resolved = cachedDispatcher;
        if (resolved != null && cachedServer == server) {
            return resolved;
        }
        synchronized (FoliaSchedulerAdapter.class) {
            if (cachedDispatcher == null || cachedServer != server) {
                cachedCapabilities = CapabilityProbe.detect(server);
                cachedDispatcher = ExecutionBackendLoader.load(server, cachedCapabilities).dispatcher();
                cachedServer = server;
            }
            return cachedDispatcher;
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
                cachedDispatcher = null;
                cachedServer = server;
            }
            return cachedCapabilities;
        }
    }

    private static TaskHandle wrap(emaki.jiuwu.craft.corelib.execution.TaskHandle handle) {
        return handle == null ? null : new ExecutionTaskHandleAdapter(handle);
    }

    private record ExecutionTaskHandleAdapter(emaki.jiuwu.craft.corelib.execution.TaskHandle delegate)
            implements TaskHandle {

        @Override
        public void cancel() {
            delegate.cancel();
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }
    }
}
