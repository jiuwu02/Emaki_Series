package emaki.jiuwu.craft.corelib.async;

import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

final class FoliaSchedulerCompat implements SchedulerCompat {

    private final Server server;
    private final boolean folia;
    private final GlobalRegionScheduler globalScheduler;
    private final RegionScheduler regionScheduler;
    private final AsyncScheduler asyncScheduler;

    private FoliaSchedulerCompat(Server server, boolean folia) {
        this.server = server;
        this.folia = folia;
        this.globalScheduler = server.getGlobalRegionScheduler();
        this.regionScheduler = server.getRegionScheduler();
        this.asyncScheduler = server.getAsyncScheduler();
    }

    static FoliaSchedulerCompat createIfSupported(Server server, boolean folia) {
        if (server == null) {
            return null;
        }
        try {
            server.getGlobalRegionScheduler();
            server.getRegionScheduler();
            server.getAsyncScheduler();
            return new FoliaSchedulerCompat(server, folia);
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
        try {
            return wrap(globalScheduler.run(plugin, ignored -> task.run()));
        } catch (Throwable throwable) {
            throw scheduleFailure("runTask", throwable);
        }
    }

    @Override
    public TaskHandle runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        long safeDelay = Math.max(1L, delayTicks);
        try {
            return wrap(globalScheduler.runDelayed(plugin, ignored -> task.run(), safeDelay));
        } catch (Throwable throwable) {
            throw scheduleFailure("runTaskLater", throwable);
        }
    }

    @Override
    public TaskHandle runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        long safeDelay = Math.max(1L, delayTicks);
        long safePeriod = Math.max(1L, periodTicks);
        try {
            return wrap(globalScheduler.runAtFixedRate(plugin, ignored -> task.run(), safeDelay, safePeriod));
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
        try {
            return wrap(entity.getScheduler().run(plugin, ignored -> task.run(), null));
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
        long safeDelay = Math.max(1L, delayTicks);
        try {
            return wrap(entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, safeDelay));
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
        try {
            return wrap(regionScheduler.run(plugin, location, ignored -> task.run()));
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
        long safeDelay = Math.max(1L, delayTicks);
        try {
            return wrap(regionScheduler.runDelayed(plugin, location, ignored -> task.run(), safeDelay));
        } catch (Throwable throwable) {
            throw scheduleFailure("runAtLocationLater", throwable);
        }
    }

    @Override
    public TaskHandle runAsync(Plugin plugin, Runnable task) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        try {
            return wrap(asyncScheduler.runNow(plugin, ignored -> task.run()));
        } catch (Throwable throwable) {
            throw scheduleFailure("runAsync", throwable);
        }
    }

    @Override
    public TaskHandle runAsyncLater(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        if (!canSchedule(plugin, task)) {
            return null;
        }
        long safeDelay = Math.max(1L, delay);
        TimeUnit safeUnit = unit == null ? TimeUnit.MILLISECONDS : unit;
        try {
            return wrap(asyncScheduler.runDelayed(plugin, ignored -> task.run(), safeDelay, safeUnit));
        } catch (Throwable throwable) {
            throw scheduleFailure("runAsyncLater", throwable);
        }
    }

    private boolean canSchedule(Plugin plugin, Runnable task) {
        return server != null && plugin != null && task != null && plugin.isEnabled();
    }

    private TaskHandle wrap(ScheduledTask task) {
        return task == null ? null : new DirectFoliaTaskHandle(task);
    }

    private static IllegalStateException scheduleFailure(String operation, Throwable throwable) {
        return new IllegalStateException("Failed to invoke Folia scheduler operation: " + operation, throwable);
    }

    private static final class DirectFoliaTaskHandle implements TaskHandle {

        private final ScheduledTask task;

        private DirectFoliaTaskHandle(ScheduledTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }
}
