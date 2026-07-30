package emaki.jiuwu.craft.storage.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.storage.api.model.StorageAmount;
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
}
