package emaki.jiuwu.craft.station.queue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerQueues {

    private final UUID playerId;
    private final Map<String, CraftQueue> queues = new LinkedHashMap<>();
    private boolean dirty;

    public PlayerQueues(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public CraftQueue queue(String stationId) {
        return queues.computeIfAbsent(stationId, id -> new CraftQueue(playerId, id));
    }

    public CraftQueue existingQueue(String stationId) {
        return stationId == null ? null : queues.get(stationId);
    }

    public Collection<CraftQueue> all() {
        return queues.values();
    }

    public Collection<String> stationIds() {
        return queues.keySet();
    }

    public int totalPendingClaims() {
        int pending = 0;
        for (CraftQueue queue : queues.values()) {
            pending += queue.pendingClaimCount();
        }
        return pending;
    }

    public void pruneEmpty() {
        queues.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public boolean isEmpty() {
        for (CraftQueue queue : queues.values()) {
            if (!queue.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }
}
