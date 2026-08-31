package emaki.jiuwu.craft.cooking.service;

import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;

final class CookingCompletionStateAccesses {

    private CookingCompletionStateAccesses() {
    }

    static CompletableFuture<Void> runAtStation(Plugin plugin, StationCoordinates coordinates, Runnable action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Location location = coordinates == null ? null : coordinates.location(0.5D, 0.5D, 0.5D);
        if (location == null || location.getWorld() == null) {
            result.completeExceptionally(new IllegalStateException("Station world is unavailable"));
            return result;
        }
        try {
            EmakiScheduling scheduler = plugin instanceof EmakiCookingPlugin cookingPlugin
                    ? cookingPlugin.taskScheduler()
                    : null;
            if (scheduler == null) {
                result.completeExceptionally(new IllegalStateException("Execution dispatcher is unavailable"));
                return result;
            }
            TaskToken handle = scheduler.runAtLocation(plugin, location, () -> {
                try {
                    action.run();
                    result.complete(null);
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
            if (handle == null) {
                result.completeExceptionally(new IllegalStateException("Station location execution was rejected"));
            }
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    static CompletableFuture<Void> requireSaved(boolean success) {
        return success
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.failedFuture(new IllegalStateException("Station state mutation was rejected as stale"));
    }
}
