package emaki.jiuwu.craft.corelib.action;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

final class ActionDispatchScheduler {

    private final Plugin plugin;

    ActionDispatchScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    CompletableFuture<ActionResult> dispatch(long delayTicks, Supplier<ActionResult> task) {
        CompletableFuture<ActionResult> future = new CompletableFuture<>();
        Runnable runnable = () -> {
            try {
                ActionResult result = task.get();
                future.complete(result == null ? ActionResult.ok() : result);
            } catch (Exception exception) {
                future.complete(ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, exception.getMessage()));
            }
        };
        if (plugin == null || !plugin.isEnabled()) {
            future.complete(ActionResult.failure(ActionErrorType.INVALID_STATE, "Source plugin is disabled."));
            return future;
        }
        if (delayTicks <= 0L && Bukkit.isPrimaryThread()) {
            runnable.run();
            return future;
        }
        if (delayTicks <= 0L) {
            plugin.getServer().getScheduler().runTask(plugin, runnable);
            return future;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, runnable, delayTicks);
        return future;
    }
}
