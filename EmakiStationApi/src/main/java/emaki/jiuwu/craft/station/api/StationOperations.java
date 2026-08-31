package emaki.jiuwu.craft.station.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.SubmitOutcome;

/**
 * State-changing station operations.
 *
 * <p>Mutating calls require an online target player. Future-returning methods dispatch work to the player's
 * owner thread, but future completion callbacks have no Bukkit-thread guarantee; schedule callback work back
 * to the relevant owner before touching players, inventories or GUIs. {@link #openGui(Player, String)} is
 * synchronous and owner-thread only.
 */
@ApiStatus.NonExtendable
public interface StationOperations {

    /**
     * Submits one craft, consuming its materials up front and queueing it.
     *
     * <p>Materials are debited at submit time rather than on completion, so the resulting queue entry
     * is itself the receipt for what the player paid. A failed debit aborts the whole submission and
     * takes nothing.
     *
     * <p>Recipes with no duration settle immediately and never occupy the queue.
     *

     * <p>Materials come from one merged inventory/warehouse pool and are spent inventory-first. The
     * {@code channel} parameter is ignored and retained only for source compatibility.
     *
     * <p><strong>Thread:</strong> any thread. Requires the target player to be online.
     *
     * @param playerId  the crafting player
     * @param stationId the station to submit at
     * @param recipeId  the recipe to craft
     * @param batch     how many times to apply the recipe; must be positive
     * @param channel   ignored; retained for source compatibility
     * @return a future carrying what the submission produced, or an explicit failure
     */
    @NotNull
    CompletableFuture<EmakiResult<SubmitOutcome>> submitAsync(@Nullable UUID playerId,
            @Nullable String stationId,
            @Nullable String recipeId,
            long batch,
            @Nullable MaterialChannel channel);

    /**
     * Cancels one queued entry and refunds its materials at the configured rate.
     *
     * <p>Refunds always return to the channel each material came from, independent of the station's
     * output routing. A partially refunded cancellation still reports success and carries the
     * shortfall as a {@code Partial} reason key.
     *
     * <p><strong>Thread:</strong> any thread. Requires the target player to be online.
     *
     * @param playerId  the queue owner
     * @param stationId the station the entry belongs to
     * @param index     the zero-based queue position to cancel
     * @return a future carrying success or an explicit failure
     */
    @NotNull
    CompletableFuture<EmakiResult<Unit>> cancelAsync(@Nullable UUID playerId,
            @Nullable String stationId,
            int index);

    /**
     * Claims every deliverable pending output the player owns across all stations.
     *
     * <p>Entries whose outputs still cannot be delivered stay pending instead of failing the call, so
     * this is safe to invoke unconditionally. The payload counts the entries actually cleared.
     *
     * <p><strong>Thread:</strong> any thread. Requires the target player to be online.
     *
     * @param playerId the claiming player
     * @return a future carrying how many entries were cleared, or an explicit failure
     */
    @NotNull
    CompletableFuture<EmakiResult<Integer>> claimAsync(@Nullable UUID playerId);

    /**
     * Opens a station GUI for a player.
     *
     * <p>Deliberately synchronous: it touches the viewer and their inventory window immediately and
     * therefore must run on the supplied player's owner thread. On Folia that is the entity scheduler
     * owner; on Paper it is the main server thread. Calls from any other thread return
     * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD} and never schedule a
     * later open on the caller's behalf.
     *
     * @param player    the online viewer
     * @param stationId the station to open
     * @return success when the window opened, otherwise an explicit failure
     */
    @NotNull
    EmakiResult<Unit> openGui(@Nullable Player player, @Nullable String stationId);
}
