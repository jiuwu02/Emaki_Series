package emaki.jiuwu.craft.mobs.listener;

import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import emaki.jiuwu.craft.mobs.skill.MobSkillExecutor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/**
 * 监听 Bukkit 实体事件，将已管理生物的行为分发到对应触发器。
 *
 * <p>支持的触发器：
 * <ul>
 *   <li>{@code on_death}      — 生物死亡（{@link EntityDeathEvent}）</li>
 *   <li>{@code on_target}     — 生物选中攻击目标（{@link EntityTargetLivingEntityEvent}）</li>
 *   <li>{@code on_damage_give}— 生物对实体造成伤害（{@link EntityDamageByEntityEvent}）</li>
 *   <li>{@code on_damage_take}— 生物受到任意来源伤害（{@link EntityDamageEvent}）</li>
 *   <li>{@code on_kill}       — 生物击杀其他实体（{@link EntityDeathEvent} + lastDamageCause）</li>
 *   <li>{@code on_shoot}      — 生物发射弹射物（{@link ProjectileLaunchEvent}）</li>
 *   <li>{@code on_explode}    — 生物爆炸（{@link EntityExplodeEvent}）</li>
 *   <li>{@code on_teleport}   — 生物传送（{@link EntityTeleportEvent}）</li>
 *   <li>{@code on_transform}  — 生物变形（{@link EntityTransformEvent}）</li>
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

    // ── 触发器：on_kill ────────────────────────────────────────────────────────

    /**
     * 当已管理生物击杀其他实体时触发 {@code on_kill}。
     *
     * <p>通过检查死亡实体的最后伤害来源确定击杀方是否为已管理生物。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityKillTarget(EntityDeathEvent event) {
        var cause = event.getEntity().getLastDamageCause();
        if (!(cause instanceof EntityDamageByEntityEvent dmgEvent)) return;
        if (!(dmgEvent.getDamager() instanceof LivingEntity killer)) return;
        String mobId = mobIdentifier.readId(killer);
        if (mobId == null) return;
        skillExecutor.executeForTrigger(killer, mobId, "on_kill");
    }

    // ── 触发器：on_shoot ───────────────────────────────────────────────────────

    /**
     * 当已管理生物发射弹射物时触发 {@code on_shoot}。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof LivingEntity shooter)) return;
        String mobId = mobIdentifier.readId(shooter);
        if (mobId == null) return;
        skillExecutor.executeForTrigger(shooter, mobId, "on_shoot");
    }

    // ── 触发器：on_explode ─────────────────────────────────────────────────────

    /**
     * 当已管理生物爆炸时触发 {@code on_explode}。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) return;
        skillExecutor.executeForTrigger(entity, mobId, "on_explode");
    }

    // ── 触发器：on_teleport ────────────────────────────────────────────────────

    /**
     * 当已管理生物传送时触发 {@code on_teleport}。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) return;
        skillExecutor.executeForTrigger(entity, mobId, "on_teleport");
    }

    // ── 触发器：on_transform ───────────────────────────────────────────────────

    /**
     * 当已管理生物发生变形（如僵尸溺水变化等）时触发 {@code on_transform}。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) return;
        skillExecutor.executeForTrigger(entity, mobId, "on_transform");
    }

    // ── 防火免疫 ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (mobIdentifier.isFireImmune(entity)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityFireDamage(EntityDamageEvent event) {
        var cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.FIRE
                && cause != EntityDamageEvent.DamageCause.FIRE_TICK
                && cause != EntityDamageEvent.DamageCause.LAVA) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (mobIdentifier.isFireImmune(entity)) event.setCancelled(true);
    }
}
