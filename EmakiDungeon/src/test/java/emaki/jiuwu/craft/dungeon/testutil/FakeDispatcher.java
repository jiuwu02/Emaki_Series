package emaki.jiuwu.craft.dungeon.testutil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class FakeDispatcher implements ExecutionDispatcher {

    @Override
    public <T> CompletableFuture<T> submitGlobal(Plugin owner, Supplier<T> task) {
        return CompletableFuture.completedFuture(task.get());
    }

    @Override
    public TaskToken runGlobal(Plugin owner, Runnable task) {
        task.run();
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runGlobalLater(Plugin owner, Runnable task, long delayTicks) {
        task.run();
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runGlobalTimer(Plugin owner, Runnable task, long delayTicks, long periodTicks) {
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runEntity(Plugin owner, Entity entity, Runnable task, Runnable retired) {
        task.run();
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runEntityLater(Plugin owner, Entity entity, Runnable task, Runnable retired,
            long delayTicks) {
        task.run();
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runAtLocation(Plugin owner, Location location, Runnable task) {
        task.run();
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runAtLocationLater(Plugin owner, Location location, Runnable task, long delayTicks) {
        task.run();
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runAsync(Plugin owner, Runnable task) {
        task.run();
        return TaskToken.UNAVAILABLE;
    }

    @Override
    public TaskToken runAsyncLater(Plugin owner, Runnable task, long delay, TimeUnit unit) {
        task.run();
        return TaskToken.UNAVAILABLE;
    }
}
