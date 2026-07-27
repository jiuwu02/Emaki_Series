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

/**
 * Handles occupancy that exceeds the current capacity.
 *
 * <p>All four policies are zero-loss. There is deliberately no {@code drop} or {@code delete}
 * option: irreversible data loss must never be triggered implicitly by an admin lowering a config
 * value.
 *
 * <p>Overflow state is <strong>not persisted</strong>. It is derived from capacity versus
 * occupancy and is recomputed whenever capacity changes — login, reload, permission change,
 * command grant — so a stored flag can never drift out of sync with the facts.
 */
public final class StorageOverflowService {

    /**
     * The evaluated overflow state.
     *
     * @param lockedKeys keys beyond the capacity boundary: readable and withdrawable, not
     *                   depositable, released as soon as they are emptied
     * @param returned   how many units were handed back to the player's inventory
     * @param rejected   whether the policy refused the shrink outright
     */
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

    private volatile AppConfig config;

    public StorageOverflowService(StorageOperationLog operationLog,
            StorageTextIndexer textIndexer,
            AppConfig config) {
        this.operationLog = operationLog;
        this.textIndexer = textIndexer;
        this.config = config;
    }

    public void reconfigure(AppConfig config) {
        if (config != null) {
            this.config = config;
        }
    }

    /**
     * Evaluates overflow and applies the configured policy.
     *
     * @param storage  the storage to evaluate
     * @param player   the online owner, or {@code null} when offline
     * @param capacity the freshly computed capacity breakdown
     * @return the resulting state
     */
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

    /**
     * {@return the keys sitting beyond the capacity boundary}
     *
     * <p>{@code compact} needs no separate branch here: entries are already stored in a compact
     * gap-free list, so "pull entries forward into free slots" is the list's natural state. What
     * remains over capacity falls back to read-only, exactly as documented.
     */
    private Set<StorageKey> overflowKeys(PlayerStorage storage, StorageCapacity capacity) {
        List<StorageKey> order = storage.entryOrder();
        int effective = Math.max(0, capacity.effectiveSlots());
        if (order.size() <= effective) {
            return Set.of();
        }
        Set<StorageKey> locked = new LinkedHashSet<>();
        for (int index = effective; index < order.size(); index++) {
            locked.add(order.get(index));
        }
        return locked;
    }

    /**
     * Tries to hand overflowing entries back to the player, locking whatever does not fit.
     */
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

    /**
     * Moves as much of an entry as the inventory will accept.
     *
     * <p>Debit-then-hand-out, with any refused remainder credited straight back, mirroring the
     * withdrawal ordering so no unit can be duplicated or silently lost.
     *
     * @return how many units left the storage
     */
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
