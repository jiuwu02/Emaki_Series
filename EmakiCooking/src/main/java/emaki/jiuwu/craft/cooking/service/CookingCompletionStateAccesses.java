package emaki.jiuwu.craft.cooking.service;

import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
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
            FoliaSchedulerAdapter.runAtLocation(plugin, location, () -> {
                try {
                    action.run();
                    result.complete(null);
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
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
