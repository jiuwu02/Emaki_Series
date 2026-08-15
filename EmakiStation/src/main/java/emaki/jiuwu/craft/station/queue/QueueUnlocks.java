package emaki.jiuwu.craft.station.queue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class QueueUnlocks {

    private final UUID playerId;
    private final Map<String, Integer> purchasedByStation = new LinkedHashMap<>();
    private boolean dirty;

    public QueueUnlocks(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public int purchased(String stationId) {
        if (stationId == null) {
            return 0;
        }
        return purchasedByStation.getOrDefault(stationId, 0);
    }

    public int addPurchased(String stationId, int slots) {
        if (stationId == null || slots < 1) {
            return purchased(stationId);
        }
        int updated = purchased(stationId) + slots;
        purchasedByStation.put(stationId, updated);
        dirty = true;
        return updated;
    }

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

    public Map<String, Integer> all() {
        return Collections.unmodifiableMap(purchasedByStation);
    }

    public boolean isEmpty() {
        return purchasedByStation.isEmpty();
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }
}
