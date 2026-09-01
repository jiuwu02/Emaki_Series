package emaki.jiuwu.craft.corelib.platform.folia.execution;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionBackend;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public final class FoliaExecutionBackend implements ExecutionBackend {

    private final Server server;
    private final GlobalRegionScheduler globalScheduler;
    private final RegionScheduler regionScheduler;
    private final AsyncScheduler asyncScheduler;

    public FoliaExecutionBackend(Server server) {
        this.server = server;
        this.globalScheduler = server.getGlobalRegionScheduler();
        this.regionScheduler = server.getRegionScheduler();
        this.asyncScheduler = server.getAsyncScheduler();
    }

    @Override
    public TaskToken runGlobal(Plugin owner, Runnable task) {
        if (!canSchedule(owner, task)) {
            return null;
        }
        try {
            return wrap(globalScheduler.run(owner, ignored -> task.run()));
        } catch (Throwable throwable) {
            throw scheduleFailure("runGlobal", throwable);
        }
    }

    @Override
    public TaskToken runGlobalLater(Plugin owner, Runnable task, long delayTicks) {
        if (!canSchedule(owner, task)) {
            return null;
        }
        try {
            return wrap(globalScheduler.runDelayed(owner, ignored -> task.run(), Math.max(1L, delayTicks)));
        } catch (Throwable throwable) {
            throw scheduleFailure("runGlobalLater", throwable);
        }
    }

    @Override
    public TaskToken runGlobalTimer(Plugin owner, Runnable task, long delayTicks, long periodTicks) {
        if (!canSchedule(owner, task)) {
            return null;
        }
        try {
            return wrap(globalScheduler.runAtFixedRate(
                    owner,
                    ignored -> task.run(),
                    Math.max(1L, delayTicks),
                    Math.max(1L, periodTicks)));
        } catch (Throwable throwable) {
            throw scheduleFailure("runGlobalTimer", throwable);
        }
    }

    @Override
    public TaskToken runEntity(Plugin owner, Entity entity, Runnable task, Runnable retired) {
        if (entity == null || !canSchedule(owner, task)) {
            return null;
        }
        Runnable retiredOnce = retired == null ? null : once(retired);
        try {
            return wrap(entity.getScheduler().run(owner, ignored -> task.run(), retiredOnce));
        } catch (Throwable throwable) {
            throw scheduleFailure("runEntity", throwable);
        }
    }

    @Override
    public TaskToken runEntityLater(Plugin owner,
            Entity entity,
            Runnable task,
            Runnable retired,
            long delayTicks) {
        if (entity == null || !canSchedule(owner, task)) {
            return null;
        }
        Runnable retiredOnce = retired == null ? null : once(retired);
        try {
            return wrap(entity.getScheduler().runDelayed(
                    owner,
                    ignored -> task.run(),
                    retiredOnce,
                    Math.max(1L, delayTicks)));
        } catch (Throwable throwable) {
            throw scheduleFailure("runEntityLater", throwable);
        }
    }

    @Override
    public TaskToken runAtLocation(Plugin owner, Location location, Runnable task) {
        if (!validLocation(location) || !canSchedule(owner, task)) {
            return null;
        }
        try {
            return wrap(regionScheduler.run(owner, location, ignored -> task.run()));
        } catch (Throwable throwable) {
            throw scheduleFailure("runAtLocation", throwable);
        }
    }

    @Override
    public TaskToken runAtLocationLater(Plugin owner, Location location, Runnable task, long delayTicks) {
        if (!validLocation(location) || !canSchedule(owner, task)) {
            return null;
        }
        try {
            return wrap(regionScheduler.runDelayed(
                    owner,
                    location,
                    ignored -> task.run(),
                    Math.max(1L, delayTicks)));
        } catch (Throwable throwable) {
            throw scheduleFailure("runAtLocationLater", throwable);
        }
    }

    @Override
    public TaskToken runAsync(Plugin owner, Runnable task) {
        if (!canSchedule(owner, task)) {
            return null;
        }
        try {
            return wrap(asyncScheduler.runNow(owner, ignored -> task.run()));
        } catch (Throwable throwable) {
            throw scheduleFailure("runAsync", throwable);
        }
    }

    @Override
    public TaskToken runAsyncLater(Plugin owner, Runnable task, long delay, TimeUnit unit) {
        if (!canSchedule(owner, task)) {
            return null;
        }
        TimeUnit safeUnit = unit == null ? TimeUnit.MILLISECONDS : unit;
        try {
            return wrap(asyncScheduler.runDelayed(
                    owner,
                    ignored -> task.run(),
                    Math.max(1L, delay),
                    safeUnit));
        } catch (Throwable throwable) {
            throw scheduleFailure("runAsyncLater", throwable);
        }
    }

    @Override
    public <T> CompletableFuture<T> submitGlobal(Plugin owner, Supplier<T> task) {
        if (task == null) {
            return CompletableFuture.failedFuture(new NullPointerException("task"));
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        TaskToken handle;
        try {
            handle = runGlobal(owner, () -> {
                try {
                    future.complete(task.get());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
            return future;
        }
        if (handle == null) {
            future.completeExceptionally(rejected(owner));
        }
        return future;
    }

    @Override
    public boolean isGlobalOwned() {
        try {
            return Bukkit.isGlobalTickThread();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public boolean isEntityOwned(Entity entity) {
        if (entity == null) {
            return false;
        }
        try {
            return Bukkit.isOwnedByCurrentRegion(entity);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public boolean isLocationOwned(Location location) {
        if (!validLocation(location)) {
            return false;
        }
        try {
            return Bukkit.isOwnedByCurrentRegion(location);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private boolean canSchedule(Plugin owner, Runnable task) {
        return server != null && owner != null && owner.isEnabled() && task != null;
    }

    private boolean validLocation(Location location) {
        return location != null;
    }

    private TaskToken wrap(ScheduledTask task) {
        return task == null ? null : new FoliaTaskHandle(task);
    }

    private static Runnable once(Runnable task) {
        AtomicBoolean invoked = new AtomicBoolean();
        return () -> {
            if (invoked.compareAndSet(false, true)) {
                task.run();
            }
        };
    }

    private static RejectedExecutionException rejected(Plugin owner) {
        String name = owner == null ? "<unknown>" : owner.getName();
        return new RejectedExecutionException("Plugin is disabled or unavailable; global execution rejected: " + name);
    }

    private static IllegalStateException scheduleFailure(String operation, Throwable throwable) {
        return new IllegalStateException("Failed to invoke Folia scheduler operation: " + operation, throwable);
    }

    private record FoliaTaskHandle(ScheduledTask task) implements TaskToken {

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean cancelled() {
            return task.isCancelled();
        }
    }
}
