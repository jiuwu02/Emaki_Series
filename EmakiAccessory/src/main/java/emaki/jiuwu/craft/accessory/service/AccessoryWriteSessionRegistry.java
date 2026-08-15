package emaki.jiuwu.craft.accessory.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AccessoryWriteSessionRegistry {

    private final Map<UUID, UUID> writers = new ConcurrentHashMap<>();

    public boolean acquire(UUID targetId, UUID viewerId) {
        if (targetId == null || viewerId == null) {
            return false;
        }
        UUID existing = writers.putIfAbsent(targetId, viewerId);
        return existing == null || existing.equals(viewerId);
    }

    public void release(UUID targetId, UUID viewerId) {
        if (targetId != null && viewerId != null) {
            writers.remove(targetId, viewerId);
        }
    }

    public void releaseAllHeldBy(UUID viewerId) {
        if (viewerId != null) {
            writers.values().removeIf(viewerId::equals);
        }
    }

    public boolean holdsLease(UUID targetId, UUID viewerId) {
        if (targetId == null || viewerId == null) {
            return false;
        }
        return viewerId.equals(writers.get(targetId));
    }

    public UUID currentWriter(UUID targetId) {
        return targetId == null ? null : writers.get(targetId);
    }

    public void clear() {
        writers.clear();
    }
}
