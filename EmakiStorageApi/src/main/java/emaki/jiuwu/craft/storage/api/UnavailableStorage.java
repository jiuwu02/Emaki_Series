package emaki.jiuwu.craft.storage.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.storage.api.model.StorageAmount;
import emaki.jiuwu.craft.storage.api.model.StorageSnapshot;

/** No-op operation layer returned while EmakiStorage has no installed bridge. */
final class UnavailableStorage implements StorageOperations {

    static final StorageOperations OPERATIONS = new UnavailableStorage();

    private UnavailableStorage() {
    }

    @Override
    public CompletableFuture<EmakiResult<StorageSnapshot>> readSnapshotAsync(UUID playerId) {
        return unavailableFuture();
    }

    @Override
    public CompletableFuture<EmakiResult<StorageAmount>> depositAsync(UUID playerId, ItemStack template, long amount) {
        return unavailableFuture();
    }

    @Override
    public CompletableFuture<EmakiResult<StorageAmount>> withdrawAsync(UUID playerId, ItemStack template, long amount) {
        return unavailableFuture();
    }

    @Override
    public CompletableFuture<EmakiResult<Long>> countOfAsync(UUID playerId, ItemStack template) {
        return unavailableFuture();
    }

    @Override
    public CompletableFuture<EmakiResult<Integer>> grantSlotsAsync(UUID playerId, int amount) {
        return unavailableFuture();
    }

    @Override
    public CompletableFuture<EmakiResult<Long>> setStackLimitAsync(UUID playerId, long limit) {
        return unavailableFuture();
    }

    @Override
    public CompletableFuture<EmakiResult<Long>> setSlotStackLimitAsync(UUID playerId, int slotIndex, long limit) {
        return unavailableFuture();
    }

    @Override
    public EmakiResult<Unit> openGui(Player player) {
        return EmakiResult.unavailable();
    }

    private static <T> CompletableFuture<EmakiResult<T>> unavailableFuture() {
        return CompletableFuture.completedFuture(EmakiResult.unavailable());
    }
}
