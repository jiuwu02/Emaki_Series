package emaki.jiuwu.craft.mobs.skill;

import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HealthPhaseTracker {

    private final Map<UUID, Set<Integer>> triggeredThresholds = new ConcurrentHashMap<>();

    public boolean hasTriggered(LivingEntity entity, int threshold) {
        Set<Integer> thresholds = triggeredThresholds.get(entity.getUniqueId());
        return thresholds != null && thresholds.contains(threshold);
    }

    public void markTriggered(LivingEntity entity, int threshold) {
        triggeredThresholds
                .computeIfAbsent(entity.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(threshold);
    }

    public void clearEntity(LivingEntity entity) {
        triggeredThresholds.remove(entity.getUniqueId());
    }

    public void clearAll() {
        triggeredThresholds.clear();
    }
}
