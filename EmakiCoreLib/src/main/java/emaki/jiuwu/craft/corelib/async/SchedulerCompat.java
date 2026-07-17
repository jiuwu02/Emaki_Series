package emaki.jiuwu.craft.corelib.async;

import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

interface SchedulerCompat {

    boolean isFolia();

    TaskHandle runTask(Plugin plugin, Runnable task);

    TaskHandle runTaskLater(Plugin plugin, Runnable task, long delayTicks);

    TaskHandle runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks);

    TaskHandle runEntityTask(Plugin plugin, Entity entity, Runnable task);

    default TaskHandle runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        return runEntityTaskLater(plugin, entity, task, null, delayTicks);
    }

    TaskHandle runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, Runnable retired, long delayTicks);

    TaskHandle runAtLocation(Plugin plugin, Location location, Runnable task);

    TaskHandle runAtLocationLater(Plugin plugin, Location location, Runnable task, long delayTicks);

    TaskHandle runAsync(Plugin plugin, Runnable task);

    TaskHandle runAsyncLater(Plugin plugin, Runnable task, long delay, TimeUnit unit);
}
