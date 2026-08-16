package emaki.jiuwu.craft.mobs.api;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import org.bukkit.entity.LivingEntity;

/**
 * EmakiMobs 专属的 Action Context Keys。
 * 
 * <p>这些 Key 用于在触发器执行时传递上下文实体，供选择器和动作使用。
 */
public final class MobActionKeys {

    /**
     * 攻击者实体（用于 on_damage_take 触发器）。
     * 
     * <p>当生物受到实体攻击时，此 Key 指向攻击方。
     * 环境伤害（摔落、岩浆等）不会设置此 Key。
     */
    public static final CoreActionKey<LivingEntity> ATTACKER = 
            CoreActionKey.of("emakimobs:attacker", LivingEntity.class);

    /**
     * 击杀者实体（用于 on_death 触发器）。
     * 
     * <p>当生物死亡时，此 Key 指向击杀方（通常是玩家）。
     * 环境死亡不会设置此 Key。
     */
    public static final CoreActionKey<LivingEntity> KILLER = 
            CoreActionKey.of("emakimobs:killer", LivingEntity.class);

    /**
     * 受害者实体（用于 on_kill 触发器）。
     * 
     * <p>当生物击杀其他实体时，此 Key 指向被击杀方。
     */
    public static final CoreActionKey<LivingEntity> VICTIM = 
            CoreActionKey.of("emakimobs:victim", LivingEntity.class);

    /**
     * 目标实体（用于 on_target 和 on_damage_give 触发器）。
     * 
     * <p>当生物锁定目标或造成伤害时，此 Key 指向目标实体。
     */
    public static final CoreActionKey<LivingEntity> TARGET = 
            CoreActionKey.of("emakimobs:target", LivingEntity.class);

    private MobActionKeys() {
        throw new UnsupportedOperationException("Utility class");
    }
}
