package emaki.jiuwu.craft.station.apiimpl;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.station.EmakiStationPlugin;
import emaki.jiuwu.craft.station.api.StationOperations;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.SubmitOutcome;
import emaki.jiuwu.craft.station.definition.StationDefinition;

/**
 * Operation layer for third-party callers.
 *
 * <p>Only the warehouse channel is reachable through the API. The inventory channel's materials live in a GUI
 * session's input slots, which only the owning player's own window can supply, so exposing it here would mean
 * inventing materials the player never placed.
 */
final class DefaultStationOperations implements StationOperations {

    private final EmakiStationPlugin plugin;

    DefaultStationOperations(EmakiStationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull CompletableFuture<EmakiResult<SubmitOutcome>> submitAsync(@Nullable UUID playerId,
            @Nullable String stationId, @Nullable String recipeId, long batch,
            @Nullable MaterialChannel channel) {
        if (playerId == null || stationId == null || recipeId == null) {
            return CompletableFuture.completedFuture(EmakiResult.invalidInput("station.submit_bad_request"));
        }
        if (batch <= 0L) {
            return CompletableFuture.completedFuture(EmakiResult.invalidInput("station.bad_batch"));
        }
        if (channel == MaterialChannel.BACKPACK) {
            return CompletableFuture.completedFuture(
                    EmakiResult.rejected("station.api_backpack_unsupported"));
        }
        return plugin.craftService().submitFromStorageAsync(playerId, stationId, recipeId, batch);
    }

    @Override
    public @NotNull CompletableFuture<EmakiResult<Unit>> cancelAsync(@Nullable UUID playerId,
            @Nullable String stationId, int index) {
        if (playerId == null || stationId == null) {
            return CompletableFuture.completedFuture(EmakiResult.invalidInput("station.cancel_bad_request"));
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(EmakiResult.targetOffline());
        }
        StationDefinition station = plugin.registry().station(stationId);
        if (station == null) {
            return CompletableFuture.completedFuture(EmakiResult.notFound("station.unknown_station"));
        }
        CompletableFuture<EmakiResult<Unit>> future = new CompletableFuture<>();
        plugin.executionDispatcher().runEntity(plugin, player,
                () -> plugin.craftService().cancelAsync(player, station, index)
                        .whenComplete((result, error) -> {
                            if (error != null) {
                                future.complete(EmakiResult.internalError("station.cancel_failed"));
                            } else {
                                future.complete(result);
                            }
                        }),
                () -> future.complete(EmakiResult.targetOffline()));
        return future;
    }

    @Override
    public @NotNull CompletableFuture<EmakiResult<Integer>> claimAsync(@Nullable UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(EmakiResult.invalidInput("station.claim_bad_request"));
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(EmakiResult.targetOffline());
        }
        CompletableFuture<EmakiResult<Integer>> future = new CompletableFuture<>();
        plugin.executionDispatcher().runEntity(plugin, player,
                () -> plugin.craftService().claimAsync(player).whenComplete((result, error) -> {
                    if (error != null) {
                        future.complete(EmakiResult.internalError("station.claim_failed"));
                    } else {
                        future.complete(result);
                    }
                }),
                () -> future.complete(EmakiResult.targetOffline()));
        return future;
    }

    @Override
    public @NotNull EmakiResult<Unit> openGui(@Nullable Player player, @Nullable String stationId) {
        if (player == null || stationId == null) {
            return EmakiResult.invalidInput("station.open_bad_request");
        }
        return plugin.stationGuiService().open(player, stationId);
    }
}
