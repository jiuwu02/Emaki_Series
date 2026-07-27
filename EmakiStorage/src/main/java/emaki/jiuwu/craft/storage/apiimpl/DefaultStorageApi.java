package emaki.jiuwu.craft.storage.apiimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.api.EmakiStorageApi;
import emaki.jiuwu.craft.storage.api.model.StorageEntrySnapshot;
import emaki.jiuwu.craft.storage.api.model.StorageResult;
import emaki.jiuwu.craft.storage.api.model.StorageSnapshot;
import emaki.jiuwu.craft.storage.log.StorageLogEntry;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.log.StorageOperationType;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;

/**
 * Bridge implementation backing {@link EmakiStorageApi}.
 *
 * <p>Every mutation is dispatched to the target player's owning thread before touching the entry
 * table, and returns a neutral {@link StorageResult} rather than throwing when the player is offline
 * or the data has not loaded. Reads fall back to loading from disk for offline players.
 */
public final class DefaultStorageApi implements EmakiStorageApi.Bridge {

    private final EmakiStoragePlugin plugin;

    public DefaultStorageApi(EmakiStoragePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String apiVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public @NotNull String pluginName() {
        return plugin.getName();
    }

    @Override
    public boolean isReady() {
        return plugin.isEnabled() && plugin.dataStore() != null && plugin.transactionService() != null;
    }

    @Override
    public @NotNull CompletableFuture<StorageSnapshot> readSnapshot(@NotNull UUID playerId) {
        if (!isReady()) {
            return CompletableFuture.completedFuture(StorageSnapshot.empty(playerId));
        }
        PlayerStorage cached = plugin.dataStore().cached(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(snapshot(cached));
        }
        return plugin.dataStore().beginSession(playerId, "")
                .thenApply(loaded -> loaded == null ? StorageSnapshot.empty(playerId) : snapshot(loaded));
    }

    private StorageSnapshot snapshot(PlayerStorage storage) {
        Player player = plugin.onlinePlayer(storage.playerId());
        var capacity = plugin.capacityService().capacityOf(storage, player,
                plugin.storageGuiService().slotsPerPage());
        List<StorageEntrySnapshot> entries = new ArrayList<>(storage.entryCount());
        List<StorageKey> order = storage.entryOrder();
        for (int index = 0; index < order.size(); index++) {
            StorageEntry entry = storage.entry(order.get(index));
            if (entry == null) {
                continue;
            }
            entries.add(entry.toSnapshot(index,
                    plugin.capacityService().effectiveStackLimit(storage, entry)));
        }
        return new StorageSnapshot(storage.playerId(), entries, capacity,
                storage.defaultStackLimit(), storage.sortMode().id());
    }

    @Override
    public @NotNull CompletableFuture<StorageResult> deposit(@NotNull UUID playerId,
            @NotNull ItemStack template, long amount) {
        return mutate(playerId, storage -> {
            Player player = plugin.onlinePlayer(playerId);
            var capacity = plugin.capacityService().capacityOf(storage, player,
                    plugin.storageGuiService().slotsPerPage());
            return plugin.transactionService().depositDirect(storage, player, capacity,
                    template, amount, StorageOperationSource.API);
        });
    }

    @Override
    public @NotNull CompletableFuture<StorageResult> withdraw(@NotNull UUID playerId,
            @NotNull ItemStack template, long amount) {
        // Routed through the shared transaction so StorageWithdrawEvent, the flow log and the
        // inventory hand-out behave identically to the GUI path. Debiting the entry directly here
        // would create a second, event-free withdrawal path that listeners could not intercept.
        return mutate(playerId, storage -> plugin.transactionService().withdraw(storage,
                plugin.onlinePlayer(playerId), StorageKey.of(template), amount,
                StorageOperationSource.API));
    }

    @Override
    public @NotNull CompletableFuture<Long> countOf(@NotNull UUID playerId, @NotNull ItemStack template) {
        return readSnapshot(playerId).thenApply(snapshot -> {
            StorageKey key = StorageKey.of(template);
            ItemStack normalized = key.toItemStack();
            for (StorageEntrySnapshot entry : snapshot.entries()) {
                if (entry.template().equals(normalized)) {
                    return entry.amount();
                }
            }
            return 0L;
        });
    }

    @Override
    public @NotNull CompletableFuture<StorageResult> grantSlots(@NotNull UUID playerId, int amount) {
        return mutate(playerId, storage -> {
            storage.grantedSlots(storage.grantedSlots() + amount);
            storage.markDirty();
            plugin.operationLog().record(StorageLogEntry.raw(playerId, StorageOperationType.ADMIN_GIVE,
                    null, (amount >= 0 ? "+" : "") + amount + "slots",
                    storage.grantedSlots(), StorageOperationSource.API, null));
            return StorageResult.success(Math.abs(amount));
        });
    }

    @Override
    public @NotNull CompletableFuture<StorageResult> setStackLimit(@NotNull UUID playerId, long limit) {
        return mutate(playerId, storage -> {
            storage.defaultStackLimit(limit);
            storage.markDirty();
            plugin.operationLog().record(StorageLogEntry.raw(playerId, StorageOperationType.ADMIN_SET,
                    null, "=" + limit, limit, StorageOperationSource.API, "field=default_stack_limit"));
            return StorageResult.success(Math.max(0L, limit));
        });
    }

    @Override
    public @NotNull CompletableFuture<StorageResult> setSlotStackLimit(@NotNull UUID playerId,
            int slotIndex, long limit) {
        return mutate(playerId, storage -> {
            StorageEntry entry = storage.entryAt(slotIndex);
            if (entry == null) {
                return StorageResult.failed(Math.max(0L, limit), "slot_empty");
            }
            entry.stackLimit(limit);
            storage.markDirty();
            plugin.operationLog().record(StorageLogEntry.raw(playerId, StorageOperationType.ADMIN_SET,
                    plugin.textIndexer().identifierOf(entry.key()), "=" + limit, limit,
                    StorageOperationSource.API, "field=slot_stack_limit slot=" + slotIndex));
            return StorageResult.success(Math.max(0L, limit));
        });
    }

    /**
     * Runs a mutation on the owning thread of a loaded storage.
     *
     * <p>Offline players are refused rather than silently mutated off-thread: the entry table is only
     * safe to touch alongside the player's own region.
     */
    private CompletableFuture<StorageResult> mutate(UUID playerId,
            java.util.function.Function<PlayerStorage, StorageResult> mutation) {
        if (!isReady()) {
            return CompletableFuture.completedFuture(StorageResult.unavailable());
        }
        Player player = plugin.onlinePlayer(playerId);
        if (player == null) {
            return CompletableFuture.completedFuture(StorageResult.unavailable());
        }
        return plugin.runOwnerWriteAsync(player, () -> {
            PlayerStorage storage = plugin.dataStore().cached(playerId);
            if (storage == null) {
                return StorageResult.unavailable();
            }
            return mutation.apply(storage);
        }, StorageResult::unavailable);
    }
}
