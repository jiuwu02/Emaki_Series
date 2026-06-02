package emaki.jiuwu.craft.corelib.async;

import org.bukkit.scheduler.BukkitTask;

final class BukkitTaskHandle implements TaskHandle {

    private final BukkitTask task;

    BukkitTaskHandle(BukkitTask task) {
        this.task = task;
    }

    @Override
    public void cancel() {
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public boolean isCancelled() {
        return task == null || task.isCancelled();
    }
}
