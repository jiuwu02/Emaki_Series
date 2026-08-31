package emaki.jiuwu.craft.corelib.execution;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;

public interface ExecutionDispatcher {

    TaskToken runGlobal(Plugin owner, Runnable task);

    TaskToken runGlobalLater(Plugin owner, Runnable task, long delayTicks);

    TaskToken runGlobalTimer(Plugin owner, Runnable task, long delayTicks, long periodTicks);

    default TaskToken runEntity(Plugin owner, Entity entity, Runnable task) {
        return runEntity(owner, entity, task, null);
    }

    TaskToken runEntity(Plugin owner, Entity entity, Runnable task, Runnable retired);

    default TaskToken runEntityLater(Plugin owner, Entity entity, Runnable task, long delayTicks) {
        return runEntityLater(owner, entity, task, null, delayTicks);
    }

    TaskToken runEntityLater(Plugin owner, Entity entity, Runnable task, Runnable retired, long delayTicks);

    TaskToken runAtLocation(Plugin owner, Location location, Runnable task);

    TaskToken runAtLocationLater(Plugin owner, Location location, Runnable task, long delayTicks);

    TaskToken runAsync(Plugin owner, Runnable task);

    TaskToken runAsyncLater(Plugin owner, Runnable task, long delay, TimeUnit unit);

    <T> CompletableFuture<T> submitGlobal(Plugin owner, Supplier<T> task);
}
