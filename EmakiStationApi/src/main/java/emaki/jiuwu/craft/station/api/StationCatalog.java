package emaki.jiuwu.craft.station.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.station.api.model.QueueSnapshot;
import emaki.jiuwu.craft.station.api.model.RecipeView;
import emaki.jiuwu.craft.station.api.model.StationView;

/**
 * Read-only queries over configured stations, recipes, and player queues.
 *
 * <p>Reached through {@link EmakiStationApi#catalog()}. The synchronous methods read already-loaded
 * configuration and are safe from any thread; the queue query is asynchronous because a queue that is
 * not cached has to be read from disk.
 */
@ApiStatus.NonExtendable
public interface StationCatalog {

    /**
     * Lists every loaded station.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @return the stations in stable id order; empty while the runtime is unavailable
     */
    @NotNull
    List<StationView> stations();

    /**
     * Looks up one station by id.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param stationId the station id; matched case-insensitively
     * @return the station, or an empty optional when it is unknown
     */
    @NotNull
    Optional<StationView> station(@Nullable String stationId);

    /**
     * Lists every loaded recipe.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @return the recipes in stable id order; empty while the runtime is unavailable
     */
    @NotNull
    List<RecipeView> recipes();

    /**
     * Looks up one recipe by id.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param recipeId the recipe id; matched case-insensitively
     * @return the recipe, or an empty optional when it is unknown
     */
    @NotNull
    Optional<RecipeView> recipe(@Nullable String recipeId);

    /**
     * Lists the recipes a station resolved from its include/exclude rules.
     *
     * <p>Permission and condition filtering is <em>not</em> applied: this is the station's configured
     * recipe set, not one particular player's visible subset.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param stationId the station id
     * @return the station's recipes, or an empty list when the station is unknown
     */
    @NotNull
    List<RecipeView> recipesOf(@Nullable String stationId);

    /**
     * Reads a detached snapshot of one player's queue at one station.
     *
     * <p>Works for offline players: queue data is per-player file state and does not need the owner
     * present. Returns an empty queue rather than a failure when the player simply has no entries.
     *
     * <p><strong>Thread:</strong> any thread. Do not assume the future completes on an owner thread.
     *
     * @param playerId  the queue owner
     * @param stationId the station id
     * @return a future completing with the snapshot or an explicit failure
     */
    @NotNull
    CompletableFuture<EmakiResult<QueueSnapshot>> queueSnapshotAsync(@Nullable UUID playerId,
            @Nullable String stationId);
}
