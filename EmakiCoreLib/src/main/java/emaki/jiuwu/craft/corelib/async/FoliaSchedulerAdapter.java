package emaki.jiuwu.craft.corelib.async;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Scheduler bridge that remains compile-time compatible with the Spigot API.
 *
 * <p>Folia scheduler classes are intentionally accessed only through
 * reflection. This keeps Emaki plugins buildable and runnable on plain Spigot
 * while still using region/entity/global schedulers when the server provides
 * Folia's runtime API.
 */
public final class FoliaSchedulerAdapter {

    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private FoliaSchedulerAdapter() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static Object runTask(Plugin plugin, Runnable task) {
        if (!canSchedule(plugin, task)) {
            runNow(task);
            return null;
        }
        if (FOLIA) {
            Object scheduler = invokeStatic(Bukkit.class, "getGlobalRegionScheduler");
            return invoke(scheduler, "run", new Class<?>[]{Plugin.class, Consumer.class}, plugin, ignoredConsumer(task));
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return null;
        }
        return plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public static Object runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (!canSchedule(plugin, task)) {
            runNow(task);
            return null;
        }
        long delay = Math.max(1L, delayTicks);
        if (FOLIA) {
            Object scheduler = invokeStatic(Bukkit.class, "getGlobalRegionScheduler");
            return invoke(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, long.class}, plugin, ignoredConsumer(task), delay);
        }
        return plugin.getServer().getScheduler().runTaskLater(plugin, task, delay);
    }

    public static Object runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (FOLIA) {
            Object scheduler = invokeStatic(Bukkit.class, "getGlobalRegionScheduler");
            return invoke(scheduler, "runAtFixedRate", new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class}, plugin, ignoredConsumer(task), delay, period);
        }
        return plugin.getServer().getScheduler().runTaskTimer(plugin, task, delay, period);
    }

    public static Object runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (entity == null) {
            return runTask(plugin, task);
        }
        if (!canSchedule(plugin, task)) {
            runNow(task);
            return null;
        }
        if (FOLIA) {
            Object scheduler = invoke(entity, "getScheduler");
            return invoke(scheduler, "run", new Class<?>[]{Plugin.class, Consumer.class, Runnable.class}, plugin, ignoredConsumer(task), null);
        }
        return runTask(plugin, task);
    }

    public static Object runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (entity == null) {
            return runTaskLater(plugin, task, delayTicks);
        }
        if (!canSchedule(plugin, task)) {
            runNow(task);
            return null;
        }
        long delay = Math.max(1L, delayTicks);
        if (FOLIA) {
            Object scheduler = invoke(entity, "getScheduler");
            return invoke(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, Runnable.class, long.class}, plugin, ignoredConsumer(task), null, delay);
        }
        return runTaskLater(plugin, task, delay);
    }

    public static Object runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (location == null) {
            return runTask(plugin, task);
        }
        if (!canSchedule(plugin, task)) {
            runNow(task);
            return null;
        }
        if (FOLIA) {
            Object scheduler = invokeStatic(Bukkit.class, "getRegionScheduler");
            return invoke(scheduler, "run", new Class<?>[]{Plugin.class, Location.class, Consumer.class}, plugin, location, ignoredConsumer(task));
        }
        return runTask(plugin, task);
    }

    public static Object runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (location == null) {
            return runTaskLater(plugin, task, delayTicks);
        }
        if (!canSchedule(plugin, task)) {
            runNow(task);
            return null;
        }
        long delay = Math.max(1L, delayTicks);
        if (FOLIA) {
            Object scheduler = invokeStatic(Bukkit.class, "getRegionScheduler");
            return invoke(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Location.class, Consumer.class, long.class}, plugin, location, ignoredConsumer(task), delay);
        }
        return runTaskLater(plugin, task, delay);
    }

    public static Object runAsync(Plugin plugin, Runnable task) {
        if (!canSchedule(plugin, task)) {
            runNow(task);
            return null;
        }
        if (FOLIA) {
            Object scheduler = invokeStatic(Bukkit.class, "getAsyncScheduler");
            return invoke(scheduler, "runNow", new Class<?>[]{Plugin.class, Consumer.class}, plugin, ignoredConsumer(task));
        }
        return plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static Object runAsyncLater(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        if (!canSchedule(plugin, task)) {
            runNow(task);
            return null;
        }
        TimeUnit safeUnit = unit == null ? TimeUnit.MILLISECONDS : unit;
        long safeDelay = Math.max(1L, delay);
        if (FOLIA) {
            Object scheduler = invokeStatic(Bukkit.class, "getAsyncScheduler");
            return invoke(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, long.class, TimeUnit.class}, plugin, ignoredConsumer(task), safeDelay, safeUnit);
        }
        long ticks = Math.max(1L, safeUnit.toMillis(safeDelay) / 50L);
        return plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, task, ticks);
    }

    public static void cancelTask(Object task) {
        if (task == null) {
            return;
        }
        if (task instanceof BukkitTask bukkitTask) {
            bukkitTask.cancel();
            return;
        }
        invokeIfPresent(task, "cancel");
    }

    public static boolean isTaskCancelled(Object task) {
        if (task == null) {
            return true;
        }
        if (task instanceof BukkitTask bukkitTask) {
            return bukkitTask.isCancelled();
        }
        Object state = invokeIfPresentWithResult(task, "isCancelled");
        if (state instanceof Boolean cancelled) {
            return cancelled;
        }
        return false;
    }

    private static boolean canSchedule(Plugin plugin, Runnable task) {
        return plugin != null && task != null && plugin.isEnabled();
    }

    private static void runNow(Runnable task) {
        if (task != null) {
            task.run();
        }
    }

    private static Consumer<Object> ignoredConsumer(Runnable task) {
        return ignored -> task.run();
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException _) {
            return false;
        }
    }

    private static Object invokeStatic(Class<?> owner, String methodName) {
        try {
            Method method = owner.getMethod(methodName);
            return method.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Missing Folia scheduler method: " + owner.getName() + '#' + methodName, exception);
        }
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Missing Folia scheduler method: " + target.getClass().getName() + '#' + methodName, exception);
        }
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Missing Folia scheduler method: " + target.getClass().getName() + '#' + methodName, exception);
        }
    }

    private static void invokeIfPresent(Object target, String methodName) {
        invokeIfPresentWithResult(target, methodName);
    }

    private static Object invokeIfPresentWithResult(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            // Unknown task handle; nothing safe to cancel or inspect.
            return null;
        }
    }
}
