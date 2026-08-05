package emaki.jiuwu.craft.accessory.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.session.SessionData;

/**
 * Mutable per-player accessory contents managed by the session cache.
 *
 * <p>Holds only {@code slotInstanceId -> ItemStack}. Part definitions are configuration facts and are
 * never written into player data, so removing a part cannot corrupt a save: the key simply stops
 * matching an active slot and becomes an orphan the player can still take back.
 *
 * <p>Keys deliberately include orphaned slots. Contribution collection iterates the active
 * configuration instead of these keys, which makes "orphans grant nothing" a property of the loop
 * rather than a condition someone has to remember to check.
 */
public final class PlayerAccessories implements SessionData<PlayerAccessories> {

    private final UUID playerId;
    private final Map<String, ItemStack> items = new LinkedHashMap<>();
    private String playerName = "";
    private long revision;
    private long persistedRevision;

    /**
     * Creates an empty payload.
     *
     * @param playerId the owning player id
     */
    public PlayerAccessories(UUID playerId) {
        this.playerId = playerId;
    }

    /** {@return the owning player id} */
    public UUID playerId() {
        return playerId;
    }

    /** {@return the last known player name; may be empty} */
    public String playerName() {
        return playerName;
    }

    /**
     * Records the last known player name for offline admin lookups.
     *
     * @param playerName the name; blank values are ignored
     */
    public void playerName(String playerName) {
        if (Texts.isNotBlank(playerName) && !playerName.equals(this.playerName)) {
            this.playerName = playerName;
            markDirty();
        }
    }

    /** {@return an unmodifiable view of every stored slot, including orphans} */
    public Map<String, ItemStack> items() {
        return Map.copyOf(items);
    }

    /** {@return the stored slot keys, including orphans} */
    public Set<String> slotKeys() {
        return Set.copyOf(items.keySet());
    }

    /**
     * Reads one slot without copying, for internal callers that only inspect the stack.
     *
     * @param slotInstanceId the slot instance id
     * @return the stored item, or {@code null} when the slot is empty
     */
    public ItemStack itemAt(String slotInstanceId) {
        return items.get(Texts.normalizeId(slotInstanceId));
    }

    /**
     * Replaces the contents of one slot.
     *
     * @param slotInstanceId the slot instance id
     * @param item           the item to store; {@code null} or air clears the slot
     * @return the previous occupant, or {@code null} when the slot was empty
     */
    public ItemStack put(String slotInstanceId, ItemStack item) {
        String key = Texts.normalizeId(slotInstanceId);
        if (Texts.isBlank(key)) {
            return null;
        }
        ItemStack previous;
        if (item == null || item.getType().isAir()) {
            previous = items.remove(key);
        } else {
            previous = items.put(key, item.clone());
        }
        markDirty();
        return previous;
    }

    /**
     * Removes one slot.
     *
     * @param slotInstanceId the slot instance id
     * @return the removed item, or {@code null} when the slot was empty
     */
    public ItemStack remove(String slotInstanceId) {
        ItemStack removed = items.remove(Texts.normalizeId(slotInstanceId));
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    /**
     * Removes every slot.
     *
     * @return the removed contents in slot order
     */
    public Map<String, ItemStack> clearAll() {
        if (items.isEmpty()) {
            return Map.of();
        }
        Map<String, ItemStack> removed = new LinkedHashMap<>(items);
        items.clear();
        markDirty();
        return Map.copyOf(removed);
    }

    /** {@return how many slots currently hold an item, including orphans} */
    public int occupiedCount() {
        return items.size();
    }

    /**
     * Replaces the whole contents without marking the payload dirty.
     *
     * <p>Used by the load path only: the values just came from disk, so treating them as a mutation
     * would schedule a pointless write-back of what was read.
     *
     * @param loaded the loaded contents
     */
    public void installLoaded(Map<String, ItemStack> loaded) {
        items.clear();
        if (loaded != null) {
            loaded.forEach((key, value) -> {
                String normalized = Texts.normalizeId(key);
                if (Texts.isNotBlank(normalized) && value != null && !value.getType().isAir()) {
                    items.put(normalized, value.clone());
                }
            });
        }
    }

    @Override
    public PlayerAccessories copy() {
        PlayerAccessories copy = new PlayerAccessories(playerId);
        copy.playerName = playerName;
        items.forEach((key, value) -> copy.items.put(key, value.clone()));
        copy.revision = revision;
        copy.persistedRevision = persistedRevision;
        return copy;
    }

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public long persistedRevision() {
        return persistedRevision;
    }

    @Override
    public boolean dirty() {
        return revision > persistedRevision;
    }

    @Override
    public void markDirty() {
        revision++;
    }

    @Override
    public void markPersisted(long revision) {
        if (revision > persistedRevision) {
            persistedRevision = revision;
        }
    }

    @Override
    public void clearDirty() {
        persistedRevision = revision;
    }
}
