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
 * <p>Reached through {@link EmakiStorageApi#operations()}. The future-returning methods may load data
 * on the storage file lane or dispatch mutations to the target player's owner thread. Their completion
 * callbacks are not guaranteed to run on a Bukkit owner thread; callers must schedule their own Bukkit
 * state access.
 *
 * <p>{@link #openGui(Player)} is deliberately synchronous: it touches the viewer and inventory GUI
 * immediately and therefore must be called on the supplied player's owner thread. It never schedules a
 * delayed open on the caller's behalf.
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
     * <p>That last part is the reason this method exists. {@link #withdrawAsync} always hands the
     * withdrawn items to the player, so using it to consume crafting materials would flash the items
     * into the inventory and take them straight back out, leaving a window in which the player can
     * see &mdash; and, with the right timing, keep &mdash; them. A batch never touches the inventory.
     *
     * <p>With {@link StorageBatchRequest#allOrNothing()} set, a single failed pre-check aborts the
     * whole batch and every stored amount stays exactly as it was. Otherwise each op is applied on a
     * best-effort basis and the result reports per-op amounts as {@code Partial}.
     *
     * <p>Pre-checks cover stock for withdrawals, and free slots, the effective stack limit,
     * {@code behavior.allow_unique_items} and the deposit filter for deposits. The same template may
     * appear several times and is accumulated in list order rather than de-duplicated.
     *
     * <p>Batch size is capped by {@code behavior.batch_max_ops}; a larger request returns
     * {@code INVALID_INPUT} with {@code batch_too_large}. Like every other mutating method this one
     * requires the target player to be online and returns {@code targetOffline()} otherwise.
     *
     * <p>Exactly one {@link emaki.jiuwu.craft.storage.api.event.StorageBatchEvent} is fired for the
     * whole batch; per-op deposit and withdraw events are deliberately not fired.
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
     * <p>Reserved units stay visible in {@link #readSnapshotAsync} through
     * {@link emaki.jiuwu.craft.storage.api.model.StorageEntrySnapshot#reservedAmount()} but are
     * excluded from what {@link #applyBatchAsync} may take, so the same units cannot be promised
     * twice.
     *
     * <p>Reservations survive a restart and are released on load once {@code ttl} has elapsed, which
     * is what keeps a crash from stranding a player's materials forever. Deposit ops in the request
     * are ignored: a reservation only holds stock back, it never pre-books capacity.
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
