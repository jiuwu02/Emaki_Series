package emaki.jiuwu.craft.mobs.skill;

import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跟踪生物的血量阶段触发状态，防止同一阈值重复触发。
 * 
 * <p>每个生物实体维护一个已触发阈值集合。当生物血量首次低于某个阈值时，
 * 触发对应技能并记录该阈值；后续即使血量继续下降也不会重复触发相同阈值。
 * 
 * <p>实体死亡或卸载后，其跟踪数据会自动清理。
 */
public final class HealthPhaseTracker {

    /** 实体 UUID → 已触发的血量阈值集合（百分比，如 75, 50, 25） */
    private final Map<UUID, Set<Integer>> triggeredThresholds = new ConcurrentHashMap<>();

    /**
     * 检查指定阈值是否已触发。
     * 
     * @param entity    生物实体
     * @param threshold 血量百分比阈值（0-100）
     * @return 如果已触发返回 true，否则返回 false
     */
    public boolean hasTriggered(LivingEntity entity, int threshold) {
        Set<Integer> thresholds = triggeredThresholds.get(entity.getUniqueId());
        return thresholds != null && thresholds.contains(threshold);
    }

    /**
     * 标记指定阈值为已触发。
     * 
     * @param entity    生物实体
     * @param threshold 血量百分比阈值（0-100）
     */
    public void markTriggered(LivingEntity entity, int threshold) {
        triggeredThresholds
                .computeIfAbsent(entity.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(threshold);
    }

    /**
     * 清除实体的所有阈值跟踪数据。
     * 
     * <p>应在实体死亡或卸载时调用。
     * 
     * @param entity 生物实体
     */
    public void clearEntity(LivingEntity entity) {
        triggeredThresholds.remove(entity.getUniqueId());
    }

    /**
     * 清除所有跟踪数据（用于插件重载）。
     */
    public void clearAll() {
        triggeredThresholds.clear();
    }
}
