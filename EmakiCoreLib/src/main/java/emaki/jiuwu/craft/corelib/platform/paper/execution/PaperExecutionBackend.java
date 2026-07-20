package emaki.jiuwu.craft.corelib.platform.paper.execution;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import emaki.jiuwu.craft.corelib.execution.ExecutionBackend;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;

public final class PaperExecutionBackend implements ExecutionBackend {

    private final Server server;

    public PaperExecutionBackend(Server server) {
        this.server = server;
    }

    @Override
    public TaskHandle runGlobal(Plugin owner, Runnable task) {
        if (!canSchedule(owner, task)) {
            return null;
        }
        try {
            return wrap(server.getScheduler().runTask(owner, task));
        } catch (Throwable throwable) {
            throw scheduleFailure("runGlobal", throwable);
        }
    }

    @Override
    public TaskHandle runGlobalLater(Plugin owner, Runnable task, long delayTicks) {
        if (!canSchedule(owner, task)) {
            return null;
        }
        try {
            return wrap(server.getScheduler().runTaskLater(owner, task, Math.max(1L, delayTicks)));
        } catch (Throwable throwable) {
            throw scheduleFailure("runGlobalLater", throwable);
        }
    }

    @Override
    public TaskHandle runGlobalTimer(Plugin owner, Runnable task, long delayTicks, long periodTicks) {
        if (!canSchedule(owner, task)) {
            return null;
        }
        try {
            return wrap(server.getScheduler().runTaskTimer(
                    owner,
                    task,
                    Math.max(1L, delayTicks),
                    Math.max(1L, periodTicks)));
        } catch (Throwable throwable) {
            throw scheduleFailure("runGlobalTimer", throwable);
        }
    }

    @Override
    public TaskHandle runEntity(Plugin owner, Entity entity, Runnable task, Runnable retired) {
        return entity == null ? null : runGlobal(owner, task);
    }

    @Override
    public TaskHandle runEntityLater(Plugin owner,
            Entity entity,
            Runnable task,
            Runnable retired,
            long delayTicks) {
        return entity == null ? null : runGlobalLater(owner, task, delayTicks);
    }

    @Override
    public TaskHandle runAtLocation(Plugin owner, Location location, Runnable task) {
        return location == null || location.getWorld() == null ? null : runGlobal(owner, task);
    }

    @Override
    public TaskHandle runAtLocationLater(Plugin owner, Location location, Runnable task, long delayTicks) {
        return location == null || location.getWorld() == null ? null : runGlobalLater(owner, task, delayTicks);
    }

    @Override
    public TaskHandle runAsync(Plugin owner, Runnable task) {
        if (!canSchedule(owner, task)) {
            return null;
        }
        try {
            return wrap(server.getScheduler().runTaskAsynchronously(owner, task));
        } catch (Throwable throwable) {
            throw scheduleFailure("runAsync", throwable);
        }
    }

    @Override
    public TaskHandle runAsyncLater(Plugin owner, Runnable task, long delay, TimeUnit unit) {
        if (!canSchedule(owner, task)) {
            return null;
        }
        TimeUnit safeUnit = unit == null ? TimeUnit.MILLISECONDS : unit;
        long ticks = Math.max(1L, safeUnit.toMillis(Math.max(1L, delay)) / 50L);
        try {
            return wrap(server.getScheduler().runTaskLaterAsynchronously(owner, task, ticks));
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
        TaskHandle handle;
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
            return Bukkit.isPrimaryThread();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public boolean isEntityOwned(Entity entity) {
        return entity != null && isGlobalOwned();
    }

    @Override
    public boolean isLocationOwned(Location location) {
        return location != null && location.getWorld() != null && isGlobalOwned();
    }

    private boolean canSchedule(Plugin owner, Runnable task) {
        return server != null && owner != null && owner.isEnabled() && task != null;
    }

    private TaskHandle wrap(BukkitTask task) {
        return task == null ? null : new PaperTaskHandle(task);
    }

    private static RejectedExecutionException rejected(Plugin owner) {
        String name = owner == null ? "<unknown>" : owner.getName();
        return new RejectedExecutionException("Plugin is disabled or unavailable; global execution rejected: " + name);
    }

    private static IllegalStateException scheduleFailure(String operation, Throwable throwable) {
        return new IllegalStateException("Failed to invoke Paper scheduler operation: " + operation, throwable);
    }

    private record PaperTaskHandle(BukkitTask task) implements TaskHandle {

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
