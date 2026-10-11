package emaki.jiuwu.craft.structure.multiblock;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TriggerCooldown {

    private final Map<String, Long> globalStamps = new ConcurrentHashMap<>();
    private final Map<String, Long> perPlayerStamps = new ConcurrentHashMap<>();

    public synchronized boolean tryAcquire(String definitionId, UUID player,
            Duration globalCooldown, Duration perPlayerCooldown) {
        if (definitionId == null || player == null) {
            return false;
        }
        long now = System.nanoTime();
        long globalNanos = globalCooldown == null ? 0L : globalCooldown.toNanos();
        long playerNanos = perPlayerCooldown == null ? 0L : perPlayerCooldown.toNanos();
        if (globalNanos > 0L) {
            Long last = globalStamps.get(definitionId);
            if (last != null && now - last < globalNanos) {
                return false;
            }
        }
        String playerKey = definitionId + "|" + player;
        if (playerNanos > 0L) {
            Long last = perPlayerStamps.get(playerKey);
            if (last != null && now - last < playerNanos) {
                return false;
            }
        }
        if (globalNanos > 0L) {
            globalStamps.put(definitionId, now);
        }
        if (playerNanos > 0L) {
            perPlayerStamps.put(playerKey, now);
        }
        return true;
    }

    public synchronized long remainingNanos(String definitionId, UUID player,
            Duration globalCooldown, Duration perPlayerCooldown) {
        long now = System.nanoTime();
        long worst = 0L;
        long globalNanos = globalCooldown == null ? 0L : globalCooldown.toNanos();
        if (globalNanos > 0L) {
            Long last = globalStamps.get(definitionId);
            if (last != null) {
                worst = Math.max(worst, globalNanos - (now - last));
            }
        }
        String playerKey = definitionId + "|" + player;
        long playerNanos = perPlayerCooldown == null ? 0L : perPlayerCooldown.toNanos();
        if (playerNanos > 0L) {
            Long last = perPlayerStamps.get(playerKey);
            if (last != null) {
                worst = Math.max(worst, playerNanos - (now - last));
            }
        }
        return Math.max(0L, worst);
    }

    public synchronized void clear() {
        globalStamps.clear();
        perPlayerStamps.clear();
    }

    public synchronized int trackedEntries() {
        return globalStamps.size() + perPlayerStamps.size();
    }
}
