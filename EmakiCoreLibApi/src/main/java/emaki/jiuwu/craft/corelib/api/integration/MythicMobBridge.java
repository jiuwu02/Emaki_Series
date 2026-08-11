package emaki.jiuwu.craft.corelib.api.integration;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;

/**
 * 按实体查询 MythicMobs 怪物元数据的统一入口。
 *
 * <p>只覆盖「读取某个 Bukkit 实体对应的 Mythic 怪物信息」这一件事。施放 Mythic 技能、
 * 注册 mechanic/condition 属于各业务模块的领域职责，不经过本桥。
 */
@ApiStatus.Experimental
public interface MythicMobBridge {

    /** {@return MythicMobs 是否已启用且本桥可用} */
    boolean available();

    /**
     * 查询实体对应的 Mythic 怪物快照。
     *
     * @param entity 目标实体，允许为 {@code null}
     * @return 快照；实体不是 Mythic 怪物、或本桥不可用时返回 {@code null}
     */
    MythicMobSnapshot snapshot(LivingEntity entity);

    /**
     * Mythic 怪物的一次性读取结果。
     *
     * @param mobId       Mythic 怪物类型 id，永不为 {@code null}
     * @param level       怪物等级
     * @param displayName 配置的显示名，未配置时为空串，永不为 {@code null}
     */
    record MythicMobSnapshot(String mobId, double level, String displayName) {
    }
}
