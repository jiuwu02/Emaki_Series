package emaki.jiuwu.craft.corelib.execution;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public interface ExecutionDispatcher {

    TaskHandle runGlobal(Plugin owner, Runnable task);

    TaskHandle runGlobalLater(Plugin owner, Runnable task, long delayTicks);

    TaskHandle runGlobalTimer(Plugin owner, Runnable task, long delayTicks, long periodTicks);

    default TaskHandle runEntity(Plugin owner, Entity entity, Runnable task) {
        return runEntity(owner, entity, task, null);
    }

    TaskHandle runEntity(Plugin owner, Entity entity, Runnable task, Runnable retired);

    default TaskHandle runEntityLater(Plugin owner, Entity entity, Runnable task, long delayTicks) {
        return runEntityLater(owner, entity, task, null, delayTicks);
    }

    TaskHandle runEntityLater(Plugin owner, Entity entity, Runnable task, Runnable retired, long delayTicks);

    TaskHandle runAtLocation(Plugin owner, Location location, Runnable task);

    TaskHandle runAtLocationLater(Plugin owner, Location location, Runnable task, long delayTicks);

    TaskHandle runAsync(Plugin owner, Runnable task);

    TaskHandle runAsyncLater(Plugin owner, Runnable task, long delay, TimeUnit unit);

    <T> CompletableFuture<T> submitGlobal(Plugin owner, Supplier<T> task);
}
