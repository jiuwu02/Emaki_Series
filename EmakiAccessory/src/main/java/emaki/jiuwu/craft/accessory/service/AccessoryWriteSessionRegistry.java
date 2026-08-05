package emaki.jiuwu.craft.accessory.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ensures at most one writable accessory window per player at a time.
 *
 * <p>This is one of the few places the design deliberately does not follow EmakiStorage. Storage lets a
 * player window and an admin window share the same cached payload with no mutual exclusion, which is
 * safe there because every Storage mutation is an atomic transaction over a quantity: interleaving two
 * sessions can reorder operations but cannot lose an item.
 *
 * <p>Accessory slots are different. Each cell holds exactly one item and edits are direct put/take, so
 * two windows writing the same cell can destroy or duplicate an item. Owner-thread serialisation alone
 * does not help: both writes are individually valid, they just disagree about what the cell held.
 *
 * <p>Read-only windows are unrestricted and may coexist with the single writer.
 */
public final class AccessoryWriteSessionRegistry {

    private final Map<UUID, UUID> writers = new ConcurrentHashMap<>();

    /**
     * Attempts to claim the write lease for a player's accessories.
     *
     * @param targetId the player whose accessories are being edited
     * @param viewerId the viewer asking to write
     * @return whether the lease was granted, or was already held by this viewer
     */
    public boolean acquire(UUID targetId, UUID viewerId) {
        if (targetId == null || viewerId == null) {
            return false;
        }
        UUID existing = writers.putIfAbsent(targetId, viewerId);
        return existing == null || existing.equals(viewerId);
    }

    /**
     * Releases the write lease when it is held by this viewer.
     *
     * @param targetId the player whose accessories were being edited
     * @param viewerId the viewer releasing the lease
     */
    public void release(UUID targetId, UUID viewerId) {
        if (targetId != null && viewerId != null) {
            writers.remove(targetId, viewerId);
        }
    }

    /**
     * Releases every lease held by one viewer, for disconnects and shutdown.
     *
     * @param viewerId the viewer whose leases should be dropped
     */
    public void releaseAllHeldBy(UUID viewerId) {
        if (viewerId != null) {
            writers.values().removeIf(viewerId::equals);
        }
    }

    /**
     * {@return whether this viewer currently holds the write lease}
     *
     * @param targetId the player whose accessories are being edited
     * @param viewerId the viewer to test
     */
    public boolean holdsLease(UUID targetId, UUID viewerId) {
        if (targetId == null || viewerId == null) {
            return false;
        }
        return viewerId.equals(writers.get(targetId));
    }

    /**
     * {@return the viewer currently holding the write lease, or {@code null} when nobody does}
     *
     * @param targetId the player whose accessories are being edited
     */
    public UUID currentWriter(UUID targetId) {
        return targetId == null ? null : writers.get(targetId);
    }

    /** Drops every lease. */
    public void clear() {
        writers.clear();
    }
}
