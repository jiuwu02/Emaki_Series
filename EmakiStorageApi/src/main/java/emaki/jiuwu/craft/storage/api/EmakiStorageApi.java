package emaki.jiuwu.craft.storage.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.storage.api.model.StorageResult;
import emaki.jiuwu.craft.storage.api.model.StorageSnapshot;

/**
 * Static public API facade for the EmakiStorage warehouse.
 *
 * <p>Third-party plugins depend on this API jar (never the implementation jar) and call these
 * static methods. EmakiStorage installs the backing {@link Bridge} during its own lifecycle;
 * when EmakiStorage is absent or still starting, every method degrades to a neutral result
 * instead of throwing.
 */
public final class EmakiStorageApi {

    private static volatile Bridge bridge;

    private EmakiStorageApi() {
    }

    /**
     * Installs the backing bridge. Intended for EmakiStorage's lifecycle only.
     *
     * @param bridge the active bridge implementation supplied by EmakiStorage
     */
    public static void install(@NotNull Bridge bridge) {
        EmakiStorageApi.bridge = bridge;
    }

    /**
     * Removes the backing bridge when it is still the active bridge.
     *
     * @param bridge the bridge to remove; ignored when it is not the active bridge
     */
    public static void uninstall(@Nullable Bridge bridge) {
        if (EmakiStorageApi.bridge == bridge) {
            EmakiStorageApi.bridge = null;
        }
    }

    /** {@return whether EmakiStorage has installed its API bridge} */
    public static boolean available() {
        return bridge != null;
    }

    /** {@return whether the backing plugin is installed and finished initialising} */
    public static boolean isAvailable() {
        Bridge resolved = bridge;
        return resolved != null && resolved.isReady();
    }

    /** {@return the semantic version string of this API, or an empty string when unavailable} */
    public static @NotNull String apiVersion() {
        Bridge resolved = bridge;
        return resolved == null ? "" : resolved.apiVersion();
    }

    /** {@return the owning plugin's name, or an empty string when unavailable} */
    public static @NotNull String pluginName() {
        Bridge resolved = bridge;
        return resolved == null ? "" : resolved.pluginName();
    }

    /** {@return whether the plugin has finished initializing and is usable} */
    public static boolean isReady() {
        Bridge resolved = bridge;
        return resolved != null && resolved.isReady();
    }

    /**
     * Reads a detached snapshot of a player's storage.
     *
     * @param playerId the storage owner
     * @return a future completing with the snapshot, or an empty snapshot when unavailable
     */
    public static @NotNull CompletableFuture<StorageSnapshot> readSnapshot(@NotNull UUID playerId) {
        Bridge resolved = bridge;
        return resolved == null
                ? CompletableFuture.completedFuture(StorageSnapshot.empty(playerId))
                : resolved.readSnapshot(playerId);
    }

    /**
     * Adds items to a player's storage.
     *
     * @param playerId the storage owner
     * @param template the item to store, located by full component equality
     * @param amount   how many units to add
     * @return a future completing with the outcome
     */
    public static @NotNull CompletableFuture<StorageResult> deposit(@NotNull UUID playerId,
            @NotNull ItemStack template, long amount) {
        Bridge resolved = bridge;
        return resolved == null
                ? CompletableFuture.completedFuture(StorageResult.unavailable())
                : resolved.deposit(playerId, template, amount);
    }

    /**
     * Removes items from a player's storage, locating the entry by the item itself.
     *
     * @param playerId the storage owner
     * @param template the item to locate, located by full component equality
     * @param amount   how many units to remove
     * @return a future completing with the outcome
     */
    public static @NotNull CompletableFuture<StorageResult> withdraw(@NotNull UUID playerId,
            @NotNull ItemStack template, long amount) {
        Bridge resolved = bridge;
        return resolved == null
                ? CompletableFuture.completedFuture(StorageResult.unavailable())
                : resolved.withdraw(playerId, template, amount);
    }

    /**
     * Counts stored units of an item.
     *
     * @param playerId the storage owner
     * @param template the item to locate
     * @return a future completing with the stored amount, {@code 0} when unavailable
     */
    public static @NotNull CompletableFuture<Long> countOf(@NotNull UUID playerId, @NotNull ItemStack template) {
        Bridge resolved = bridge;
        return resolved == null
                ? CompletableFuture.completedFuture(0L)
                : resolved.countOf(playerId, template);
    }

    /**
     * Adjusts the command/API granted slot pool.
     *
     * @param playerId the storage owner
     * @param amount   slots to add; negative values reclaim slots
     * @return a future completing with the outcome
     */
    public static @NotNull CompletableFuture<StorageResult> grantSlots(@NotNull UUID playerId, int amount) {
        Bridge resolved = bridge;
        return resolved == null
                ? CompletableFuture.completedFuture(StorageResult.unavailable())
                : resolved.grantSlots(playerId, amount);
    }

    /**
     * Sets the player-level default per-slot ceiling.
     *
     * @param playerId the storage owner
     * @param limit    the new ceiling; {@code 0} restores config inheritance
     * @return a future completing with the outcome
     */
    public static @NotNull CompletableFuture<StorageResult> setStackLimit(@NotNull UUID playerId, long limit) {
        Bridge resolved = bridge;
        return resolved == null
                ? CompletableFuture.completedFuture(StorageResult.unavailable())
                : resolved.setStackLimit(playerId, limit);
    }

    /**
     * Sets a per-entry ceiling for one logical slot.
     *
     * @param playerId  the storage owner
     * @param slotIndex the logical slot index
     * @param limit     the new ceiling; {@code 0} restores player-default inheritance
     * @return a future completing with the outcome
     */
    public static @NotNull CompletableFuture<StorageResult> setSlotStackLimit(@NotNull UUID playerId,
            int slotIndex, long limit) {
        Bridge resolved = bridge;
        return resolved == null
                ? CompletableFuture.completedFuture(StorageResult.unavailable())
                : resolved.setSlotStackLimit(playerId, slotIndex, limit);
    }

    /** Internal bridge installed by EmakiStorage. */
    public interface Bridge extends StorageApi {
    }
}
