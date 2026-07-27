package emaki.jiuwu.craft.storage.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.storage.api.model.StorageResult;
import emaki.jiuwu.craft.storage.api.model.StorageSnapshot;

/**
 * Contract for EmakiStorage's public operations.
 *
 * <p>Items are located by the item itself rather than by an opaque handle: every method that
 * takes an {@link ItemStack} normalises it internally (clone, {@code setAmount(1)}) and looks the
 * entry up by full {@code ItemStack#equals} comparison. Callers never need to pre-compute a key.
 *
 * <p>All mutating methods return a {@link CompletableFuture} because the entry table may only be
 * touched on the owning entity thread; the implementation schedules the work there and completes
 * the future afterwards. A future never completes exceptionally for ordinary rejection — those
 * arrive as {@link StorageResult} statuses instead.
 */
public interface StorageApi {

    /** {@return the semantic version string of the backing plugin} */
    @NotNull
    String apiVersion();

    /** {@return the owning plugin's name} */
    @NotNull
    String pluginName();

    /** {@return whether the backing plugin has finished initialising} */
    boolean isReady();

    /**
     * Reads a detached snapshot of a player's storage, loading it from disk when necessary.
     *
     * @param playerId the storage owner
     * @return a future completing with the snapshot, or an empty snapshot when unavailable
     */
    @NotNull
    CompletableFuture<StorageSnapshot> readSnapshot(@NotNull UUID playerId);

    /**
     * Adds items to a player's storage without touching any inventory.
     *
     * <p>Partial application is expected when the per-slot limit or slot capacity is reached;
     * inspect {@link StorageResult#appliedAmount()}.
     *
     * @param playerId the storage owner
     * @param template the item to store; only its identity matters, its amount is ignored
     * @param amount   how many units to add, must be positive
     * @return a future completing with the outcome
     */
    @NotNull
    CompletableFuture<StorageResult> deposit(@NotNull UUID playerId, @NotNull ItemStack template, long amount);

    /**
     * Removes items from a player's storage without giving them to anyone.
     *
     * @param playerId the storage owner
     * @param template the item to locate, matched by full component equality
     * @param amount   how many units to remove, must be positive
     * @return a future completing with the outcome
     */
    @NotNull
    CompletableFuture<StorageResult> withdraw(@NotNull UUID playerId, @NotNull ItemStack template, long amount);

    /**
     * Counts how many units of an item a player has stored.
     *
     * @param playerId the storage owner
     * @param template the item to locate, matched by full component equality
     * @return a future completing with the stored amount, {@code 0} when absent
     */
    @NotNull
    CompletableFuture<Long> countOf(@NotNull UUID playerId, @NotNull ItemStack template);

    /**
     * Adjusts the command/API granted slot pool.
     *
     * @param playerId the storage owner
     * @param amount   slots to add; negative values reclaim slots
     * @return a future completing with the outcome
     */
    @NotNull
    CompletableFuture<StorageResult> grantSlots(@NotNull UUID playerId, int amount);

    /**
     * Sets the player-level default per-slot ceiling.
     *
     * @param playerId the storage owner
     * @param limit    the new ceiling; {@code 0} restores inheritance from {@code config.yml}
     * @return a future completing with the outcome
     */
    @NotNull
    CompletableFuture<StorageResult> setStackLimit(@NotNull UUID playerId, long limit);

    /**
     * Sets a per-entry ceiling for one logical slot.
     *
     * @param playerId  the storage owner
     * @param slotIndex the logical slot index
     * @param limit     the new ceiling; {@code 0} restores inheritance from the player default
     * @return a future completing with the outcome
     */
    @NotNull
    CompletableFuture<StorageResult> setSlotStackLimit(@NotNull UUID playerId, int slotIndex, long limit);
}
