package emaki.jiuwu.craft.storage.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.log.StorageLogEntry;
import emaki.jiuwu.craft.storage.log.StorageOperationLog;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.log.StorageOperationType;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;

public final class StorageOverflowService {

    public record OverflowState(Set<StorageKey> lockedKeys, long returned, boolean rejected) {

        public OverflowState(Set<StorageKey> lockedKeys, long returned, boolean rejected) {
            this.lockedKeys = Set.copyOf(lockedKeys);
            this.returned = returned;
            this.rejected = rejected;
        }

        public static OverflowState none() {
            return new OverflowState(Set.of(), 0L, false);
        }

        public boolean hasOverflow() {
            return !lockedKeys.isEmpty();
        }

        public boolean locked(StorageKey key) {
            return lockedKeys.contains(key);
        }
    }

    private final StorageOperationLog operationLog;
    private final StorageTextIndexer textIndexer;
    private final StorageCapacityService capacityService;

    private volatile AppConfig config;

    public StorageOverflowService(StorageOperationLog operationLog,
            StorageTextIndexer textIndexer,
            StorageCapacityService capacityService,
            AppConfig config) {
        this.operationLog = operationLog;
        this.textIndexer = textIndexer;
        this.capacityService = capacityService;
        this.config = config;
    }

    public void reconfigure(AppConfig config) {
        if (config != null) {
            this.config = config;
        }
    }

    public OverflowState evaluate(PlayerStorage storage, Player player, StorageCapacity capacity) {
        if (storage == null || !capacity.overflowing()) {
            return OverflowState.none();
        }
        AppConfig.OverflowPolicy policy = config.unlock().overflowPolicy();
        return switch (policy) {
            case REJECT_CHANGE -> new OverflowState(overflowKeys(storage, capacity), 0L, true);
            case RETURN_INVENTORY -> returnToInventory(storage, player, capacity);
            case COMPACT, LOCK_READONLY -> new OverflowState(overflowKeys(storage, capacity), 0L, false);
        };
    }

    private Set<StorageKey> overflowKeys(PlayerStorage storage, StorageCapacity capacity) {
        List<StorageKey> order = storage.entryOrder();
        int effective = Math.max(0, capacity.effectiveSlots());
        Set<StorageKey> locked = new LinkedHashSet<>();
        long consumed = 0L;
        for (StorageKey key : order) {
            StorageEntry entry = storage.entry(key);
            if (entry == null) {
                continue;
            }
            if (consumed >= effective) {
                locked.add(key);
                continue;
            }
            consumed += capacityService.slotSpan(storage, entry);
            if (consumed > effective) {
                locked.add(key);
            }
        }
        return locked.isEmpty() ? Set.of() : locked;
    }

    private OverflowState returnToInventory(PlayerStorage storage, Player player, StorageCapacity capacity) {
        Set<StorageKey> candidates = overflowKeys(storage, capacity);
        if (candidates.isEmpty()) {
            return OverflowState.none();
        }
        if (player == null || !player.isOnline()) {
            return new OverflowState(candidates, 0L, false);
        }
        long returned = 0L;
        Set<StorageKey> stillLocked = new LinkedHashSet<>();
        for (StorageKey key : candidates) {
            StorageEntry entry = storage.entry(key);
            if (entry == null || entry.empty()) {
                continue;
            }
            long moved = pushToInventory(player, key, entry);
            if (moved > 0L) {
                returned += moved;
                operationLog.record(StorageLogEntry.of(storage.playerId(), StorageOperationType.OVERFLOW,
                        textIndexer.identifierOf(key), -moved, entry.amount(),
                        StorageOperationSource.COMMAND, "policy=return_inventory"));
            }
            if (entry.empty()) {
                storage.remove(key);
            } else {
                stillLocked.add(key);
            }
        }
        if (returned > 0L) {
            storage.markDirty();
        }
        return new OverflowState(stillLocked, returned, false);
    }

    private long pushToInventory(Player player, StorageKey key, StorageEntry entry) {
        int stackSize = key.vanillaMaxStackSize();
        long moved = 0L;
        List<ItemStack> refused = new ArrayList<>();
        while (!entry.empty()) {
            int take = (int) Math.min(stackSize, entry.amount());
            long debited = entry.remove(take);
            if (debited <= 0L) {
                break;
            }
            ItemStack stack = key.toItemStack((int) debited);
            var leftover = InventoryItemUtil.addOrDrop(player, stack);
            long rejected = 0L;
            if (leftover != null && !leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    if (item != null) {
                        rejected += item.getAmount();
                        refused.add(item);
                    }
                }
            }
            moved += debited - rejected;
            if (rejected > 0L) {
                entry.add(rejected, Long.MAX_VALUE);
                break;
            }
        }
        refused.clear();
        return moved;
    }
}
