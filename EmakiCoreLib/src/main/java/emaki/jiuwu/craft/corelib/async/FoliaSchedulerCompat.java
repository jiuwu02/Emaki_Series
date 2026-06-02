package emaki.jiuwu.craft.corelib.async;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

final class FoliaSchedulerCompat implements SchedulerCompat {

    private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();
    private static final String SCHEDULER_PACKAGE = "io.papermc.paper.threadedregions.scheduler.";
    private static final String GLOBAL_REGION_SCHEDULER_CLASS = SCHEDULER_PACKAGE + "GlobalRegionScheduler";
    private static final String REGION_SCHEDULER_CLASS = SCHEDULER_PACKAGE + "RegionScheduler";
    private static final String ASYNC_SCHEDULER_CLASS = SCHEDULER_PACKAGE + "AsyncScheduler";
    private static final String ENTITY_SCHEDULER_CLASS = SCHEDULER_PACKAGE + "EntityScheduler";
    private static final String SCHEDULED_TASK_CLASS = SCHEDULER_PACKAGE + "ScheduledTask";

    private final boolean folia;
    private final MethodHandle globalRun;
    private final MethodHandle globalRunDelayed;
    private final MethodHandle globalRunAtFixedRate;
    private final MethodHandle regionRun;
    private final MethodHandle regionRunDelayed;
    private final MethodHandle asyncRunNow;
    private final MethodHandle asyncRunDelayed;
    private final MethodHandle entityGetScheduler;
    private final MethodHandle entityRun;
    private final MethodHandle entityRunDelayed;
    private final MethodHandle taskCancel;
    private final MethodHandle taskIsCancelled;

    private FoliaSchedulerCompat(boolean folia,
            MethodHandle globalRun,
            MethodHandle globalRunDelayed,
            MethodHandle globalRunAtFixedRate,
            MethodHandle regionRun,
            MethodHandle regionRunDelayed,
            MethodHandle asyncRunNow,
            MethodHandle asyncRunDelayed,
            MethodHandle entityGetScheduler,
            MethodHandle entityRun,
            MethodHandle entityRunDelayed,
            MethodHandle taskCancel,
            MethodHandle taskIsCancelled) {
        this.folia = folia;
        this.globalRun = globalRun;
        this.globalRunDelayed = globalRunDelayed;
        this.globalRunAtFixedRate = globalRunAtFixedRate;
        this.regionRun = regionRun;
        this.regionRunDelayed = regionRunDelayed;
        this.asyncRunNow = asyncRunNow;
        this.asyncRunDelayed = asyncRunDelayed;
        this.entityGetScheduler = entityGetScheduler;
        this.entityRun = entityRun;
        this.entityRunDelayed = entityRunDelayed;
        this.taskCancel = taskCancel;
        this.taskIsCancelled = taskIsCancelled;
    }

    static FoliaSchedulerCompat createIfSupported(Server server, boolean folia) {
        if (server == null) {
            return null;
        }
        try {
            ClassLoader classLoader = server.getClass().getClassLoader();
            Class<?> globalSchedulerClass = loadClass(classLoader, GLOBAL_REGION_SCHEDULER_CLASS);
            Class<?> regionSchedulerClass = loadClass(classLoader, REGION_SCHEDULER_CLASS);
            Class<?> asyncSchedulerClass = loadClass(classLoader, ASYNC_SCHEDULER_CLASS);
            Class<?> entitySchedulerClass = loadClass(classLoader, ENTITY_SCHEDULER_CLASS);
            Class<?> scheduledTaskClass = loadClass(classLoader, SCHEDULED_TASK_CLASS);

            MethodHandle getGlobalRegionScheduler = bindVirtual(
                    Server.class,
                    "getGlobalRegionScheduler",
                    MethodType.methodType(globalSchedulerClass),
                    MethodType.methodType(Object.class, Server.class)
            );
            MethodHandle getRegionScheduler = bindVirtual(
                    Server.class,
                    "getRegionScheduler",
                    MethodType.methodType(regionSchedulerClass),
                    MethodType.methodType(Object.class, Server.class)
            );
            MethodHandle getAsyncScheduler = bindVirtual(
                    Server.class,
                    "getAsyncScheduler",
                    MethodType.methodType(asyncSchedulerClass),
                    MethodType.methodType(Object.class, Server.class)
            );

            Object globalScheduler = getGlobalRegionScheduler.invokeExact(server);
            Object regionScheduler = getRegionScheduler.invokeExact(server);
            Object asyncScheduler = getAsyncScheduler.invokeExact(server);

            MethodHandle entityGetScheduler = bindVirtual(
                    Entity.class,
                    "getScheduler",
                    MethodType.methodType(entitySchedulerClass),
                    MethodType.methodType(Object.class, Entity.class)
            );

            MethodHandle globalRun = bindBound(
                    globalScheduler,
                    globalSchedulerClass,
                    "run",
                    MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class),
                    MethodType.methodType(Object.class, Plugin.class, Consumer.class)
            );
            MethodHandle globalRunDelayed = bindBound(
                    globalScheduler,
                    globalSchedulerClass,
                    "runDelayed",
                    MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, long.class),
                    MethodType.methodType(Object.class, Plugin.class, Consumer.class, long.class)
            );
            MethodHandle globalRunAtFixedRate = bindBound(
                    globalScheduler,
                    globalSchedulerClass,
                    "runAtFixedRate",
                    MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, long.class, long.class),
                    MethodType.methodType(Object.class, Plugin.class, Consumer.class, long.class, long.class)
            );
            MethodHandle regionRun = bindBound(
                    regionScheduler,
                    regionSchedulerClass,
                    "run",
                    MethodType.methodType(scheduledTaskClass, Plugin.class, Location.class, Consumer.class),
                    MethodType.methodType(Object.class, Plugin.class, Location.class, Consumer.class)
            );
            MethodHandle regionRunDelayed = bindBound(
                    regionScheduler,
                    regionSchedulerClass,
                    "runDelayed",
                    MethodType.methodType(scheduledTaskClass, Plugin.class, Location.class, Consumer.class, long.class),
                    MethodType.methodType(Object.class, Plugin.class, Location.class, Consumer.class, long.class)
            );
            MethodHandle asyncRunNow = bindBound(
                    asyncScheduler,
                    asyncSchedulerClass,
                    "runNow",
                    MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class),
                    MethodType.methodType(Object.class, Plugin.class, Consumer.class)
            );
            MethodHandle asyncRunDelayed = bindBound(
                    asyncScheduler,
                    asyncSchedulerClass,
                    "runDelayed",
                    MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, long.class, TimeUnit.class),
                    MethodType.methodType(Object.class, Plugin.class, Consumer.class, long.class, TimeUnit.class)
            );
            MethodHandle entityRun = bindVirtual(
                    entitySchedulerClass,
                    "run",
                    MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, Runnable.class),
                    MethodType.methodType(Object.class, Object.class, Plugin.class, Consumer.class, Runnable.class)
            );
            MethodHandle entityRunDelayed = bindVirtual(
                    entitySchedulerClass,
                    "runDelayed",
                    MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, Runnable.class, long.class),
                    MethodType.methodType(Object.class, Object.class, Plugin.class, Consumer.class, Runnable.class, long.class)
            );
            MethodHandle taskCancel = bindVirtual(
                    scheduledTaskClass,
                    "cancel",
                    MethodType.methodType(loadCancelledStateClass(classLoader)),
                    MethodType.methodType(Object.class, Object.class)
            );
            MethodHandle taskIsCancelled = bindVirtual(
                    scheduledTaskClass,
                    "isCancelled",
                    MethodType.methodType(boolean.class),
                    MethodType.methodType(boolean.class, Object.class)
            );

            return new FoliaSchedulerCompat(
                    folia,
                    globalRun,
                    globalRunDelayed,
                    globalRunAtFixedRate,
                    regionRun,
                    regionRunDelayed,
                    asyncRunNow,
                    asyncRunDelayed,
                    entityGetScheduler,
                    entityRun,
                    entityRunDelayed,
                    taskCancel,
                    taskIsCancelled
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public boolean isFolia() {
        return folia;
    }

    @Override
    public TaskHandle runTask(Plugin plugin, Runnable task) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        Consumer<Object> consumer = ignored -> task.run();
        try {
            return wrap((Object) globalRun.invokeExact(plugin, consumer));
        } catch (Throwable throwable) {
            throw scheduleFailure("runTask", throwable);
        }
    }

    @Override
    public TaskHandle runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        Consumer<Object> consumer = ignored -> task.run();
        long safeDelay = Math.max(1L, delayTicks);
        try {
            return wrap((Object) globalRunDelayed.invokeExact(plugin, consumer, safeDelay));
        } catch (Throwable throwable) {
            throw scheduleFailure("runTaskLater", throwable);
        }
    }

    @Override
    public TaskHandle runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        Consumer<Object> consumer = ignored -> task.run();
        long safeDelay = Math.max(1L, delayTicks);
        long safePeriod = Math.max(1L, periodTicks);
        try {
            return wrap((Object) globalRunAtFixedRate.invokeExact(plugin, consumer, safeDelay, safePeriod));
        } catch (Throwable throwable) {
            throw scheduleFailure("runTaskTimer", throwable);
        }
    }

    @Override
    public TaskHandle runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (entity == null || !folia) {
            return runTask(plugin, task);
        }
        if (!canSchedule(plugin, task)) {
            return null;
        }
        Consumer<Object> consumer = ignored -> task.run();
        try {
            Object entityScheduler = entityGetScheduler.invokeExact(entity);
            return wrap((Object) entityRun.invokeExact(entityScheduler, plugin, consumer, (Runnable) null));
        } catch (Throwable throwable) {
            throw scheduleFailure("runEntityTask", throwable);
        }
    }

    @Override
    public TaskHandle runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (entity == null || !folia) {
            return runTaskLater(plugin, task, delayTicks);
        }
        if (!canSchedule(plugin, task)) {
            return null;
        }
        Consumer<Object> consumer = ignored -> task.run();
        long safeDelay = Math.max(1L, delayTicks);
        try {
            Object entityScheduler = entityGetScheduler.invokeExact(entity);
            return wrap((Object) entityRunDelayed.invokeExact(entityScheduler, plugin, consumer, (Runnable) null, safeDelay));
        } catch (Throwable throwable) {
            throw scheduleFailure("runEntityTaskLater", throwable);
        }
    }

    @Override
    public TaskHandle runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (location == null || !folia) {
            return runTask(plugin, task);
        }
        if (!canSchedule(plugin, task)) {
            return null;
        }
        Consumer<Object> consumer = ignored -> task.run();
        try {
            return wrap((Object) regionRun.invokeExact(plugin, location, consumer));
        } catch (Throwable throwable) {
            throw scheduleFailure("runAtLocation", throwable);
        }
    }

    @Override
    public TaskHandle runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (location == null || !folia) {
            return runTaskLater(plugin, task, delayTicks);
        }
        if (!canSchedule(plugin, task)) {
            return null;
        }
        Consumer<Object> consumer = ignored -> task.run();
        long safeDelay = Math.max(1L, delayTicks);
        try {
            return wrap((Object) regionRunDelayed.invokeExact(plugin, location, consumer, safeDelay));
        } catch (Throwable throwable) {
            throw scheduleFailure("runAtLocationLater", throwable);
        }
    }

    @Override
    public TaskHandle runAsync(Plugin plugin, Runnable task) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        Consumer<Object> consumer = ignored -> task.run();
        try {
            return wrap((Object) asyncRunNow.invokeExact(plugin, consumer));
        } catch (Throwable throwable) {
            throw scheduleFailure("runAsync", throwable);
        }
    }

    @Override
    public TaskHandle runAsyncLater(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        Consumer<Object> consumer = ignored -> task.run();
        long safeDelay = Math.max(1L, delay);
        TimeUnit safeUnit = unit == null ? TimeUnit.MILLISECONDS : unit;
        try {
            return wrap((Object) asyncRunDelayed.invokeExact(plugin, consumer, safeDelay, safeUnit));
        } catch (Throwable throwable) {
            throw scheduleFailure("runAsyncLater", throwable);
        }
    }

    private boolean canSchedule(Plugin plugin, Runnable task) {
        return plugin != null && task != null && plugin.isEnabled();
    }

    private TaskHandle wrap(Object task) {
        return task == null ? null : new FoliaTaskHandle(task, taskCancel, taskIsCancelled);
    }

    private static Class<?> loadClass(ClassLoader classLoader, String className) throws ClassNotFoundException {
        return Class.forName(className, false, classLoader);
    }

    private static Class<?> loadCancelledStateClass(ClassLoader classLoader) throws ClassNotFoundException {
        return loadClass(classLoader, SCHEDULED_TASK_CLASS + "$CancelledState");
    }

    private static MethodHandle bindBound(Object target,
            Class<?> owner,
            String methodName,
            MethodType runtimeType,
            MethodType callType) throws NoSuchMethodException, IllegalAccessException {
        return PUBLIC_LOOKUP.findVirtual(owner, methodName, runtimeType)
                .bindTo(target)
                .asType(callType);
    }

    private static MethodHandle bindVirtual(Class<?> owner,
            String methodName,
            MethodType runtimeType,
            MethodType callType) throws NoSuchMethodException, IllegalAccessException {
        return PUBLIC_LOOKUP.findVirtual(owner, methodName, runtimeType)
                .asType(callType);
    }

    private static IllegalStateException scheduleFailure(String operation, Throwable throwable) {
        return new IllegalStateException("Failed to invoke Folia scheduler operation: " + operation, throwable);
    }
}
