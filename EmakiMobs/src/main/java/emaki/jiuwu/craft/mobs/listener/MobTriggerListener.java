package emaki.jiuwu.craft.mobs.listener;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.mobs.api.MobActionKeys;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import emaki.jiuwu.craft.mobs.skill.HealthPhaseTracker;
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

import java.util.Map;

public final class MobTriggerListener implements Listener {

    private final MobIdentifier mobIdentifier;
    private final MobSkillExecutor skillExecutor;
    private final HealthPhaseTracker healthPhaseTracker;

    public MobTriggerListener(MobIdentifier mobIdentifier, MobSkillExecutor skillExecutor,
                              HealthPhaseTracker healthPhaseTracker) {
        this.mobIdentifier = mobIdentifier;
        this.skillExecutor = skillExecutor;
        this.healthPhaseTracker = healthPhaseTracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) {
            return;
        }
        LivingEntity killer = entity.getKiller();
        if (killer != null) {
            skillExecutor.executeForTrigger(entity, mobId, "on_death",
                    Map.of(MobActionKeys.KILLER, killer));
        } else {
            skillExecutor.executeForTrigger(entity, mobId, "on_death");
        }

        healthPhaseTracker.clearEntity(entity);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) {
            return;
        }
        LivingEntity target = event.getTarget();
        if (target != null) {
            skillExecutor.executeForTrigger(entity, mobId, "on_target",
                    Map.of(MobActionKeys.TARGET, target));
        } else {
            skillExecutor.executeForTrigger(entity, mobId, "on_target");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageBy(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) {
            return;
        }
        String mobId = mobIdentifier.readId(attacker);
        if (mobId == null) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity target) {
            skillExecutor.executeForTrigger(attacker, mobId, "on_damage_give",
                    Map.of(MobActionKeys.TARGET, target));
        } else {
            skillExecutor.executeForTrigger(attacker, mobId, "on_damage_give");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        String mobId = mobIdentifier.readId(target);
        if (mobId == null) {
            return;
        }

        if (event instanceof EntityDamageByEntityEvent dmgEvent) {
            if (dmgEvent.getDamager() instanceof LivingEntity attacker) {
                skillExecutor.executeForTrigger(target, mobId, "on_damage_take",
                        Map.of(MobActionKeys.ATTACKER, attacker));
            } else {
                skillExecutor.executeForTrigger(target, mobId, "on_damage_take");
            }
        } else {
            skillExecutor.executeForTrigger(target, mobId, "on_damage_take");
        }

        checkHealthThresholds(target, mobId);
    }

    private void checkHealthThresholds(LivingEntity entity, String mobId) {
        double healthPercent = (entity.getHealth() / entity.getMaxHealth()) * 100;

        int[] thresholds = {90, 75, 50, 25, 10};

        for (int threshold : thresholds) {
            if (healthPercent <= threshold && !healthPhaseTracker.hasTriggered(entity, threshold)) {
                String triggerName = "on_health_threshold_" + threshold;
                skillExecutor.executeForTrigger(entity, mobId, triggerName);
                healthPhaseTracker.markTriggered(entity, threshold);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityKillTarget(EntityDeathEvent event) {
        var cause = event.getEntity().getLastDamageCause();
        if (!(cause instanceof EntityDamageByEntityEvent dmgEvent)) return;
        if (!(dmgEvent.getDamager() instanceof LivingEntity killer)) return;
        String mobId = mobIdentifier.readId(killer);
        if (mobId == null) return;
        LivingEntity victim = event.getEntity();
        skillExecutor.executeForTrigger(killer, mobId, "on_kill",
                Map.of(MobActionKeys.VICTIM, victim));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof LivingEntity shooter)) return;
        String mobId = mobIdentifier.readId(shooter);
        if (mobId == null) return;
        skillExecutor.executeForTrigger(shooter, mobId, "on_shoot");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) return;
        skillExecutor.executeForTrigger(entity, mobId, "on_explode");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) return;
        skillExecutor.executeForTrigger(entity, mobId, "on_teleport");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) return;
        skillExecutor.executeForTrigger(entity, mobId, "on_transform");
    }

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
