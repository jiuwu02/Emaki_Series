package emaki.jiuwu.craft.station.queue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every queue one player owns, keyed by station id.
 *
 * <p>Held in memory per online player and persisted as one file per player. The dirty flag is what keeps
 * autosave from rewriting untouched files.
 *
 * <p>Not thread-safe; {@link QueueService} confines access to the owner's owner thread.
 */
public final class PlayerQueues {

    private final UUID playerId;
    private final Map<String, CraftQueue> queues = new LinkedHashMap<>();
    private boolean dirty;

    /**
     * Creates an empty holder.
     *
     * @param playerId the owner
     */
    public PlayerQueues(UUID playerId) {
        this.playerId = playerId;
    }

    /** {@return the owner} */
    public UUID playerId() {
        return playerId;
    }

    /**
     * Returns the queue for a station, creating an empty one when absent.
     *
     * @param stationId the station id
     * @return the queue
     */
    public CraftQueue queue(String stationId) {
        return queues.computeIfAbsent(stationId, id -> new CraftQueue(playerId, id));
    }

    /**
     * Returns the queue for a station without creating one.
     *
     * @param stationId the station id
     * @return the queue, or {@code null} when the player has none there
     */
    public CraftQueue existingQueue(String stationId) {
        return stationId == null ? null : queues.get(stationId);
    }

    /** {@return every queue this player owns} */
    public Collection<CraftQueue> all() {
        return queues.values();
    }

    /** {@return every station id this player has a queue at} */
    public Collection<String> stationIds() {
        return queues.keySet();
    }

    /** {@return how many entries across all stations are waiting to be claimed} */
    public int totalPendingClaims() {
        int pending = 0;
        for (CraftQueue queue : queues.values()) {
            pending += queue.pendingClaimCount();
        }
        return pending;
    }

    /** Drops every queue that holds no entries, so empty files do not accumulate. */
    public void pruneEmpty() {
        queues.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /** {@return whether this player owns no entries at all} */
    public boolean isEmpty() {
        for (CraftQueue queue : queues.values()) {
            if (!queue.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** {@return whether this player's data changed since the last save} */
    public boolean dirty() {
        return dirty;
    }

    /** Marks this player's data as needing a save. */
    public void markDirty() {
        this.dirty = true;
    }

    /** Clears the dirty flag after a successful save. */
    public void clearDirty() {
        this.dirty = false;
    }
}
