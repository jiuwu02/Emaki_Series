package emaki.jiuwu.craft.storage.api;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.storage.api.model.ReservationHandle;
import emaki.jiuwu.craft.storage.api.model.StorageAmount;
import emaki.jiuwu.craft.storage.api.model.StorageBatchRequest;
import emaki.jiuwu.craft.storage.api.model.StorageBatchResult;
import emaki.jiuwu.craft.storage.api.model.StorageSnapshot;

/**
 * EmakiStorage queries and state-changing operations.
 *
 * <p>Future-returning methods may use storage I/O and dispatch player mutations; their completion thread is
 * not guaranteed to own Bukkit state. {@link #openGui(Player)} is synchronous and requires the supplied
 * player's owner thread; it never schedules a later open.
 */
@ApiStatus.NonExtendable
public interface StorageOperations {

    /**
     * Reads a detached snapshot, loading the storage from disk when it is not cached.
     *
     * <p><strong>Thread:</strong> any thread. Do not assume the future completes on an owner thread.
     *
     * @param playerId the storage owner
     * @return a future completing with the snapshot or an explicit failure
     */
    @NotNull
    CompletableFuture<EmakiResult<StorageSnapshot>> readSnapshotAsync(@Nullable UUID playerId);

    /**
     * Adds items to storage without taking them from an inventory.
     *
     * <p><strong>Thread:</strong> any thread. The runtime dispatches the mutation to the target
     * player's owner thread.
     *
     * @param playerId the storage owner
     * @param template the item identity; stack amount is ignored
     * @param amount   the positive number of units requested
     * @return a future carrying requested and actually applied amounts
     */
    @NotNull
    CompletableFuture<EmakiResult<StorageAmount>> depositAsync(@Nullable UUID playerId,
            @Nullable ItemStack template, long amount);

    /**
     * Removes items from storage and gives them to the online storage owner.
     *
     * <p><strong>Thread:</strong> any thread. The runtime dispatches the storage and inventory mutation
     * to the target player's owner thread.
     *
     * @param playerId the storage owner
     * @param template the item identity to withdraw
     * @param amount   the positive number of units requested
     * @return a future carrying requested and actually applied amounts
     */
    @NotNull
    CompletableFuture<EmakiResult<StorageAmount>> withdrawAsync(@Nullable UUID playerId,
            @Nullable ItemStack template, long amount);

    /**
     * Counts stored units of an item.
     *
     * <p><strong>Thread:</strong> any thread. Do not assume the future completes on an owner thread.
     *
     * @param playerId the storage owner
     * @param template the item identity to count
     * @return a future carrying the real count, including a legitimate zero
     */
    @NotNull
    CompletableFuture<EmakiResult<Long>> countOfAsync(@Nullable UUID playerId, @Nullable ItemStack template);

    /**
     * Adjusts the command/API-granted slot pool.
     *
     * <p><strong>Thread:</strong> any thread. The runtime dispatches the mutation to the target
     * player's owner thread.
     *
     * @param playerId the storage owner
     * @param amount   slots to add; negative values reclaim slots
     * @return a future carrying the resulting granted-slot total
     */
    @NotNull
    CompletableFuture<EmakiResult<Integer>> grantSlotsAsync(@Nullable UUID playerId, int amount);

    /**
     * Sets the player-level default per-slot ceiling.
     *
     * <p><strong>Thread:</strong> any thread. The runtime dispatches the mutation to the target
     * player's owner thread.
     *
     * @param playerId the storage owner
     * @param limit    the non-negative ceiling; zero restores config inheritance
     * @return a future carrying the applied ceiling
     */
    @NotNull
    CompletableFuture<EmakiResult<Long>> setStackLimitAsync(@Nullable UUID playerId, long limit);

    /**
     * Sets the ceiling for one logical storage entry.
     *
     * <p><strong>Thread:</strong> any thread. The runtime dispatches the mutation to the target
     * player's owner thread.
     *
     * @param playerId  the storage owner
     * @param slotIndex the zero-based logical slot index
     * @param limit     the non-negative ceiling; zero restores player-default inheritance
     * @return a future carrying the applied ceiling
     */
    @NotNull
    CompletableFuture<EmakiResult<Long>> setSlotStackLimitAsync(@Nullable UUID playerId,
            int slotIndex, long limit);

    /**
     * Opens the player's own storage GUI.
     *
     * <p><strong>Thread:</strong> the supplied player's owner thread. On Folia this is the entity
     * scheduler owner; on Paper it is the main server thread. Calls from any other thread return
     * {@code WRONG_THREAD} and do not schedule a later open.
     *
     * @param player the online viewer and storage owner
     * @return success when the window opened, otherwise an explicit failure
     */
    @NotNull
    EmakiResult<Unit> openGui(@Nullable Player player);

    /**
     * Pre-checks and commits a batch of signed increments in one pass on the target player's owner
     * thread, <strong>without routing anything through the player's inventory</strong>.
     *
     *
     *
     *
     *
     *
     * <p>{@link StorageBatchRequest#allOrNothing()} makes any failed pre-check abort the entire batch;
     * otherwise operations are best-effort and the payload reports per-operation amounts as partial when
     * needed. Withdrawal stock, deposit capacity/limits/filters and repeated templates are evaluated in list
     * order. Exactly one {@link emaki.jiuwu.craft.storage.api.event.StorageBatchEvent} covers the batch;
     * per-operation deposit/withdraw events are not emitted.
     *
     * <p><strong>Thread:</strong> any thread. Do not assume the future completes on an owner thread.
     *
     * @param playerId the storage owner
     * @param request  the increments and their failure mode
     * @return a future carrying per-op amounts, or an explicit failure
     */
    @NotNull
    CompletableFuture<EmakiResult<StorageBatchResult>> applyBatchAsync(@Nullable UUID playerId,
            @Nullable StorageBatchRequest request);

    /**
     * Counts several templates in one pass instead of N {@link #countOfAsync} round trips.
     *
     * <p>The returned map is keyed by the caller's own template instances: storage identity is full
     * {@link ItemStack#equals(Object)}, which is exactly {@code ItemStack}'s own
     * {@code equals}/{@code hashCode} contract, so they are safe map keys. A template that is not in
     * storage maps to {@code 0L} rather than being absent, so callers never have to distinguish
     * "missing key" from "zero stock".
     *
     * <p>Available for offline players, like {@link #readSnapshotAsync} and {@link #countOfAsync}.
     *
     * <p><strong>Thread:</strong> any thread. Do not assume the future completes on an owner thread.
     *
     * @param playerId  the storage owner
     * @param templates the item identities to count
     * @return a future carrying one count per requested template
     */
    @NotNull
    CompletableFuture<EmakiResult<Map<ItemStack, Long>>> countAllAsync(@Nullable UUID playerId,
            @Nullable Collection<ItemStack> templates);

    /**
     * Holds the withdrawal side of a batch without applying it, so the units can neither be spent
     * elsewhere nor be handed out until the reservation is committed or released.
     *
     * <p>Reserved units remain visible through {@link emaki.jiuwu.craft.storage.api.model.StorageEntrySnapshot#reservedAmount()}
     * but are unavailable to other withdrawals. Holds survive restart until {@code ttl} expires; deposit
     * operations are ignored because a reservation holds stock only, not future capacity.
     *
     * <p><strong>Thread:</strong> any thread. Requires the target player to be online.
     *
     * @param playerId the storage owner
     * @param request  the increments whose withdrawals should be held
     * @param ttl      how long the hold survives without a commit; non-positive values are rejected
     * @return a future carrying the ticket, or an explicit failure
     */
    @NotNull
    CompletableFuture<EmakiResult<ReservationHandle>> reserveAsync(@Nullable UUID playerId,
            @Nullable StorageBatchRequest request, @Nullable Duration ttl);

    /**
     * Applies a previously reserved batch and drops the hold.
     *
     * <p>Commit re-runs the deposit-side pre-checks, because free slots and stack limits may have
     * changed since the hold was taken; the withdrawal side is already guaranteed by the hold itself.
     *
     * <p><strong>Thread:</strong> any thread. Requires the target player to be online.
     *
     * @param handle the ticket returned by {@link #reserveAsync}
     * @return a future carrying per-op amounts, or an explicit failure
     */
    @NotNull
    CompletableFuture<EmakiResult<StorageBatchResult>> commitAsync(@Nullable ReservationHandle handle);

    /**
     * Drops a hold without applying it, returning the reserved units to normal circulation.
     *
     * <p>Idempotent: releasing an unknown or already-released ticket returns {@code NOT_FOUND} rather
     * than failing loudly, so a cleanup path can call it unconditionally.
     *
     * <p><strong>Thread:</strong> any thread. Requires the target player to be online.
     *
     * @param handle the ticket returned by {@link #reserveAsync}
     * @return a future carrying success or an explicit failure
     */
    @NotNull
    CompletableFuture<EmakiResult<Unit>> releaseAsync(@Nullable ReservationHandle handle);
}
