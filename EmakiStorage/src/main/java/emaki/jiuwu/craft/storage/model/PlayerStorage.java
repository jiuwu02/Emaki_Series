package emaki.jiuwu.craft.storage.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregate root for one player's warehouse.
 *
 * <p>{@code entries} and {@code entryOrder} must stay in lockstep: additions append to the tail,
 * and emptying an entry removes it from both so later entries shift forward naturally. The index
 * into {@code entryOrder} <em>is</em> the logical slot number, which is why the list is kept
 * compact and gap-free.
 *
 * <p>This type is not thread-safe by design. Every mutation happens on the owning entity thread
 * because deposits and withdrawals are atomic trades against the player's inventory, and Bukkit
 * inventories may only be touched there.
 */
public final class PlayerStorage {

    private final UUID playerId;
    private final Map<StorageKey, StorageEntry> entries = new HashMap<>();
    private final List<StorageKey> entryOrder = new ArrayList<>();

    private String playerName = "";
    private int grantedSlots;
    private int purchasedSlots;
    private long defaultStackLimit;
    private SortMode sortMode = SortMode.AMOUNT_DESC;
    private boolean autoPickupEnabled;
    private long nextTemplateId = 1L;

    private long revision;
    private long persistedRevision;

    public PlayerStorage(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    /** Records the last known name purely for human troubleshooting; never an identity source. */
    public void playerName(String playerName) {
        this.playerName = playerName == null ? "" : playerName;
    }

    public int grantedSlots() {
        return grantedSlots;
    }

    public void grantedSlots(int grantedSlots) {
        this.grantedSlots = grantedSlots;
    }

    public int purchasedSlots() {
        return Math.max(0, purchasedSlots);
    }

    public void purchasedSlots(int purchasedSlots) {
        this.purchasedSlots = Math.max(0, purchasedSlots);
    }

    /** {@return the player-level ceiling; {@code 0} means inherit {@code capacity.default_stack_limit}} */
    public long defaultStackLimit() {
        return defaultStackLimit;
    }

    public void defaultStackLimit(long defaultStackLimit) {
        this.defaultStackLimit = Math.max(0L, defaultStackLimit);
    }

    public SortMode sortMode() {
        return sortMode;
    }

    public void sortMode(SortMode sortMode) {
        if (sortMode != null) {
            this.sortMode = sortMode;
        }
    }

    /** {@return 该玩家是否开启了自动拾取} */
    public boolean autoPickupEnabled() {
        return autoPickupEnabled;
    }

    public void autoPickupEnabled(boolean autoPickupEnabled) {
        this.autoPickupEnabled = autoPickupEnabled;
    }

    public long nextTemplateId() {
        return nextTemplateId;
    }

    public void nextTemplateId(long nextTemplateId) {
        this.nextTemplateId = Math.max(1L, nextTemplateId);
    }

    /** {@return and consume the next per-player template id} */
    public long allocateTemplateId() {
        long allocated = nextTemplateId;
        nextTemplateId = allocated + 1L;
        return allocated;
    }

    public Map<StorageKey, StorageEntry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    public List<StorageKey> entryOrder() {
        return Collections.unmodifiableList(entryOrder);
    }

    public int entryCount() {
        return entryOrder.size();
    }

    public StorageEntry entry(StorageKey key) {
        return key == null ? null : entries.get(key);
    }

    /** {@return the entry at a logical slot index, or {@code null} when out of range} */
    public StorageEntry entryAt(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= entryOrder.size()) {
            return null;
        }
        return entries.get(entryOrder.get(slotIndex));
    }

    /** {@return the logical slot index of a key, or {@code -1} when absent} */
    public int indexOf(StorageKey key) {
        return key == null ? -1 : entryOrder.indexOf(key);
    }

    /**
     * Appends a new entry to the tail of the order list.
     *
     * @param entry the entry to add; ignored when its key already exists
     * @return the assigned logical slot index, or the existing index when already present
     */
    public int append(StorageEntry entry) {
        if (entry == null) {
            return -1;
        }
        StorageEntry existing = entries.get(entry.key());
        if (existing != null) {
            return entryOrder.indexOf(entry.key());
        }
        entries.put(entry.key(), entry);
        entryOrder.add(entry.key());
        return entryOrder.size() - 1;
    }

    /**
     * Removes an entry from both structures, letting later entries shift forward.
     *
     * @param key the key to drop
     * @return whether an entry was actually removed
     */
    public boolean remove(StorageKey key) {
        if (key == null || entries.remove(key) == null) {
            return false;
        }
        entryOrder.remove(key);
        return true;
    }

    /** Drops every entry whose amount reached zero, preserving relative order of the rest. */
    public int pruneEmpty() {
        int removed = 0;
        for (int index = entryOrder.size() - 1; index >= 0; index--) {
            StorageKey key = entryOrder.get(index);
            StorageEntry entry = entries.get(key);
            if (entry == null || entry.empty()) {
                entries.remove(key);
                entryOrder.remove(index);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Replaces the whole order list, used by the explicit sort action.
     *
     * @param order the new order; keys unknown to this storage are skipped, and any known key
     *              missing from {@code order} is appended so no entry can ever be lost
     */
    public void reorder(List<StorageKey> order) {
        if (order == null || order.isEmpty()) {
            return;
        }
        List<StorageKey> rebuilt = new ArrayList<>(entryOrder.size());
        Map<StorageKey, Boolean> seen = new LinkedHashMap<>();
        for (StorageKey key : order) {
            if (entries.containsKey(key) && seen.putIfAbsent(key, Boolean.TRUE) == null) {
                rebuilt.add(key);
            }
        }
        for (StorageKey key : entryOrder) {
            if (seen.putIfAbsent(key, Boolean.TRUE) == null) {
                rebuilt.add(key);
            }
        }
        entryOrder.clear();
        entryOrder.addAll(rebuilt);
    }

    public long revision() {
        return revision;
    }

    public long persistedRevision() {
        return persistedRevision;
    }

    /** {@return whether the in-memory state differs from what was last written to disk} */
    public boolean dirty() {
        return revision > persistedRevision;
    }

    public void markDirty() {
        revision++;
    }

    public void markPersisted(long revision) {
        this.persistedRevision = Math.max(this.persistedRevision, revision);
    }

    public void clearDirty() {
        persistedRevision = revision;
    }

    /**
     * Deep-copies this storage for off-thread serialisation.
     *
     * <p>Entries are copied; keys are shared because {@link StorageKey} is immutable and its
     * template is never handed out by reference.
     *
     * @return a detached copy safe to hand to an async file lane
     */
    public PlayerStorage copy() {
        PlayerStorage copy = new PlayerStorage(playerId);
        copy.playerName = playerName;
        copy.grantedSlots = grantedSlots;
        copy.purchasedSlots = purchasedSlots;
        copy.defaultStackLimit = defaultStackLimit;
        copy.sortMode = sortMode;
        copy.autoPickupEnabled = autoPickupEnabled;
        copy.nextTemplateId = nextTemplateId;
        copy.revision = revision;
        copy.persistedRevision = persistedRevision;
        for (StorageKey key : entryOrder) {
            StorageEntry entry = entries.get(key);
            if (entry == null) {
                continue;
            }
            copy.append(new StorageEntry(key, entry.amount(), entry.stackLimit(),
                    entry.searchText(), entry.sortName()));
        }
        return copy;
    }
}
