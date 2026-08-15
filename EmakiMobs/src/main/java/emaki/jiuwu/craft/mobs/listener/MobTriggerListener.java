package emaki.jiuwu.craft.mobs.listener;

import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import emaki.jiuwu.craft.mobs.skill.MobSkillExecutor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * 监听 Bukkit 实体事件，将已管理生物的行为分发到对应触发器。
 *
 * <p>支持的触发器：
 * <ul>
 *   <li>{@code on_death}      — 生物死亡（{@link EntityDeathEvent}）</li>
 *   <li>{@code on_target}     — 生物选中攻击目标（{@link EntityTargetLivingEntityEvent}）</li>
 *   <li>{@code on_damage_give}— 生物对实体造成伤害（{@link EntityDamageByEntityEvent}）</li>
 *   <li>{@code on_damage_take}— 生物受到任意来源伤害（{@link EntityDamageEvent}）</li>
 * </ul>
 *
 * <p>所有处理器使用 {@link EventPriority#MONITOR} 优先级，在其他插件完成判断后才触发技能。
 * 若事件已被取消，技能不触发（{@code ignoreCancelled = true}）。
 */
public final class MobTriggerListener implements Listener {

    private final MobIdentifier mobIdentifier;
    private final MobSkillExecutor skillExecutor;

    public MobTriggerListener(MobIdentifier mobIdentifier, MobSkillExecutor skillExecutor) {
        this.mobIdentifier = mobIdentifier;
        this.skillExecutor = skillExecutor;
    }

    // ── 触发器：on_death ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) {
            return;
        }
        skillExecutor.executeForTrigger(entity, mobId, "on_death");
    }

    // ── 触发器：on_target ──────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) {
            return;
        }
        skillExecutor.executeForTrigger(entity, mobId, "on_target");
    }

    // ── 触发器：on_damage_give ─────────────────────────────────────────────

    /**
     * 当已管理生物作为攻击方造成伤害时触发 {@code on_damage_give}。
     *
     * <p>使用 {@link EntityDamageByEntityEvent} 确保攻击方是实体（包含生物和玩家）。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageBy(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) {
            return;
        }
        String mobId = mobIdentifier.readId(attacker);
        if (mobId == null) {
            return;
        }
        skillExecutor.executeForTrigger(attacker, mobId, "on_damage_give");
    }

    // ── 触发器：on_damage_take ─────────────────────────────────────────────

    /**
     * 当已管理生物受到任意来源的伤害时触发 {@code on_damage_take}。
     *
     * <p>使用基类 {@link EntityDamageEvent}，涵盖被玩家/实体攻击、
     * 摔落、岩浆、窒息等所有伤害来源。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        String mobId = mobIdentifier.readId(target);
        if (mobId == null) {
            return;
        }
        skillExecutor.executeForTrigger(target, mobId, "on_damage_take");
    }
}
