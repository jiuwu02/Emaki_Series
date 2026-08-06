package emaki.jiuwu.craft.station.queue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How many queue slots one player has bought, per station.
 *
 * <h2>Why this is not part of {@link PlayerQueues}</h2>
 * {@link QueueStore} deletes a player's {@code queue.yml} outright when their queues are empty, which is
 * correct for entries — an empty queue has nothing to say. Purchased slots are paid for and must survive a
 * player emptying their queue, so they live in their own file that is never deleted for being "empty".
 *
 * <p>Only counts are stored, never which slots. A slot has no identity; the number is the whole fact.
 *
 * <p>Not thread-safe. Every access happens on the owning player's owner thread.
 */
public final class QueueUnlocks {

    private final UUID playerId;
    private final Map<String, Integer> purchasedByStation = new LinkedHashMap<>();
    private boolean dirty;

    /**
     * Creates an empty record.
     *
     * @param playerId the owning player
     */
    public QueueUnlocks(UUID playerId) {
        this.playerId = playerId;
    }

    /** {@return the owning player} */
    public UUID playerId() {
        return playerId;
    }

    /**
     * Reads how many slots a player has bought at one station.
     *
     * @param stationId the station to read
     * @return the purchased count; zero when nothing was bought there
     */
    public int purchased(String stationId) {
        if (stationId == null) {
            return 0;
        }
        return purchasedByStation.getOrDefault(stationId, 0);
    }

    /**
     * Records additional purchased slots at one station.
     *
     * @param stationId the station bought at
     * @param slots     how many slots were bought; values below one are ignored
     * @return the new total for that station
     */
    public int addPurchased(String stationId, int slots) {
        if (stationId == null || slots < 1) {
            return purchased(stationId);
        }
        int updated = purchased(stationId) + slots;
        purchasedByStation.put(stationId, updated);
        dirty = true;
        return updated;
    }

    /**
     * Overwrites the purchased count at one station, which is what loading and administrative edits do.
     *
     * @param stationId the station to set
     * @param slots     the count; negatives are clamped to zero
     */
    public void setPurchased(String stationId, int slots) {
        if (stationId == null) {
            return;
        }
        int safe = Math.max(0, slots);
        if (safe == 0) {
            purchasedByStation.remove(stationId);
        } else {
            purchasedByStation.put(stationId, safe);
        }
        dirty = true;
    }

    /** {@return an unmodifiable view of every station with purchased slots} */
    public Map<String, Integer> all() {
        return Collections.unmodifiableMap(purchasedByStation);
    }

    /** {@return whether this record has nothing to persist} */
    public boolean isEmpty() {
        return purchasedByStation.isEmpty();
    }

    /** {@return whether this record has unsaved changes} */
    public boolean dirty() {
        return dirty;
    }

    /** Marks this record as needing a save. */
    public void markDirty() {
        dirty = true;
    }

    /** Clears the unsaved-changes flag once a save completes. */
    public void clearDirty() {
        dirty = false;
    }
}
