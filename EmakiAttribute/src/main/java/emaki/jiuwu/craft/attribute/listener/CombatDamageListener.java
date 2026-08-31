package emaki.jiuwu.craft.attribute.listener;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.config.DamageCauseRule;
import emaki.jiuwu.craft.attribute.api.model.DamageContext;
import emaki.jiuwu.craft.attribute.api.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.CombatSupport;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;

public final class CombatDamageListener implements Listener {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;
    private final CombatDebugHandler debugHandler;
    private final EmakiScheduling scheduling;

    public CombatDamageListener(EmakiAttributePlugin plugin,
            AttributeService attributeService,
            CombatDebugHandler debugHandler,
            EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.attributeService = attributeService;
        this.debugHandler = debugHandler;
        this.scheduling = scheduling;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        Entity shooter = projectile.getShooter() instanceof Entity entity ? entity : null;
        if (shooter instanceof LivingEntity livingEntity) {
            Player playerShooter = livingEntity instanceof Player player ? player : null;
            var snapshot = attributeService.snapshotProjectile(projectile, livingEntity);
            if (debugHandler.shouldDebugCombat(livingEntity, null, projectile)) {
                debugHandler.debugCombat(livingEntity, null, projectile, "PROJECTILE_LAUNCH", "combat_debug.projectile_launch_snapshot_written", Map.of(
                        "projectile", debugHandler.describeEntity(projectile),
                        "damage_type", snapshot == null ? "<none>" : snapshot.damageTypeId(),
                        "signature", snapshot == null ? "<none>" : snapshot.sourceSignature()
                ));
            }
            if (playerShooter != null && !attributeService.isAttackCoolingDown(playerShooter)) {
                attributeService.startAttackCooldown(
                        playerShooter,
                        snapshot == null ? null : snapshot.attackSnapshot(),
                        playerShooter.getInventory().getItemInMainHand()
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileHitEntity(ProjectileHitEvent event) {
        if (!attributeService.config().sameSignatureIgnoresInvulnerabilityEnabled()
                || !(event.getHitEntity() instanceof LivingEntity target)) {
            return;
        }
        Projectile projectile = event.getEntity();
        var snapshot = attributeService.readProjectileSnapshot(projectile);
        if (snapshot == null
                || !attributeService.attackBatchInvulnerabilityGate().matchesCurrentBatch(target, snapshot)) {
            return;
        }
        target.setNoDamageTicks(0);
        target.setLastDamage(0D);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getPlayer() != null && attributeService.isAttackCoolingDown(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getPlayer() != null && attributeService.isAttackCoolingDown(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        LivingEntity target = event.getEntity() instanceof LivingEntity livingEntity ? livingEntity : null;
        if (target == null) {
            return;
        }
        Entity damager = event.getDamager();
        if (attributeService.isSyntheticDamage(target)) {
            LivingEntity syntheticAttacker = damager instanceof LivingEntity livingEntity ? livingEntity : null;
            Projectile syntheticProjectile = damager instanceof Projectile projectile ? projectile : null;
            debugHandler.debugCombat(syntheticAttacker, target, syntheticProjectile, "SYNTHETIC_DAMAGE_BYPASS", "combat_debug.synthetic_damage_bypass_entity");
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.PROJECTILE) {
            if (damager instanceof Projectile projectile) {
                Entity shooter = projectile.getShooter() instanceof Entity entity ? entity : null;
                LivingEntity shootingEntity = shooter instanceof LivingEntity livingEntity ? livingEntity : null;
                if (!attributeService.config().vanillaEventDamageEnabled()
                        && attributeService.config().damageCauseRule(cause.name()) == null) {
                    handleEnvironmentalDamage(event, target, shootingEntity);
                    return;
                }
                if (debugHandler.shouldDebugCombat(shootingEntity, target, projectile)) {
                    debugHandler.debugCombat(shootingEntity, target, projectile, "PROJECTILE_HIT", "combat_debug.projectile_hit_intercept", Map.of(
                            "shooter", debugHandler.describeEntity(shootingEntity),
                            "projectile", debugHandler.describeEntity(projectile),
                            "target", debugHandler.describeEntity(target),
                            "cause", cause.name(),
                            "vanilla_damage", debugHandler.formatNumber(event.getDamage()),
                            "vanilla_final", debugHandler.formatNumber(event.getFinalDamage())
                    ));
                }
                DamageContextVariables context = CombatSupport.baseContext(event, target);
                DamageContext damageContext = createProjectileDamageContext(event, projectile, target, context);
                if (damageContext == null) {
                    debugHandler.debugCombat(shootingEntity, target, projectile, "PROJECTILE_RESOLVE_EMPTY", "combat_debug.projectile_resolve_empty");
                    handleEnvironmentalDamage(event, target, shootingEntity);
                    return;
                }
                if (attributeService.config().vanillaEventDamageEnabled()) {
                    applyPerfectTakeover(event, damageContext, shootingEntity, target, projectile, projectile);
                    return;
                }
                event.setCancelled(true);
                resolveAndApplyDamage(
                        attributeService.resolveDamageApplicationAsync(damageContext),
                        shootingEntity,
                        target,
                        projectile,
                        projectile,
                        event.getFinalDamage(),
                        "PROJECTILE_RESOLVE_EMPTY",
                        "combat_debug.projectile_resolve_empty",
                        "PROJECTILE_RESOLVED",
                        "combat_debug.projectile_resolved",
                        "PROJECTILE_APPLY",
                        "combat_debug.projectile_apply"
                );
                return;
            }
            handleEnvironmentalDamage(event, target, damager instanceof LivingEntity livingEntity ? livingEntity : null);
            return;
        }
        if (attributeService.config().vanillaEventDamageEnabled()) {
            LivingEntity takeoverAttacker = damager instanceof LivingEntity livingEntity ? livingEntity : null;
            if ((cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
                    && isMeleeAttackCoolingDown(
                            takeoverAttacker instanceof Player player ? player : null,
                            takeoverAttacker,
                            target)) {
                event.setCancelled(true);
                return;
            }
            handleEnvironmentalDamage(event, target, takeoverAttacker);
            return;
        }
        if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            if (damager instanceof LivingEntity attacker) {
                DamageContextVariables context = CombatSupport.baseContext(event, target);
                if (debugHandler.shouldDebugCombat(attacker, target, null)) {
                    debugHandler.debugCombat(attacker, target, null, "MELEE_HIT", "combat_debug.melee_hit_intercept", Map.of(
                            "attacker", debugHandler.describeEntity(attacker),
                            "target", debugHandler.describeEntity(target),
                            "cause", cause.name(),
                            "vanilla_damage", debugHandler.formatNumber(event.getDamage()),
                            "vanilla_final", debugHandler.formatNumber(event.getFinalDamage())
                    ));
                }
                Player attackingPlayer = attacker instanceof Player player ? player : null;
                if (isMeleeAttackCoolingDown(attackingPlayer, attacker, target)) {
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                DamageContext damageContext = createMeleeDamageContext(event, attacker, target, context);
                if (damageContext == null) {
                    debugHandler.debugCombat(attacker, target, null, "MELEE_RESOLVE_EMPTY", "combat_debug.melee_resolve_empty");
                    return;
                }
                resolveAndApplyDamage(
                        attributeService.resolveDamageApplicationAsync(damageContext),
                        attacker,
                        target,
                        null,
                        damager,
                        event.getFinalDamage(),
                        "MELEE_RESOLVE_EMPTY",
                        "combat_debug.melee_resolve_empty",
                        "MELEE_RESOLVED",
                        "combat_debug.melee_resolved",
                        "MELEE_APPLY",
                        "combat_debug.melee_apply"
                );
                return;
            }
            handleEnvironmentalDamage(event, target, null);
            return;
        }
        handleEnvironmentalDamage(event, target, damager instanceof LivingEntity livingEntity ? livingEntity : null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        LivingEntity target = event.getEntity() instanceof LivingEntity livingEntity ? livingEntity : null;
        if (target == null) {
            return;
        }
        if (attributeService.isSyntheticDamage(target)) {
            debugHandler.debugCombat(null, target, null, "SYNTHETIC_DAMAGE_BYPASS", "combat_debug.synthetic_damage_bypass_non_entity");
            return;
        }
        handleEnvironmentalDamage(event, target, null);
    }

    private boolean handleEnvironmentalDamage(EntityDamageEvent event, LivingEntity target, LivingEntity attacker) {
        AttributeConfig config = attributeService.config();
        boolean vanillaEventDamage = config.vanillaEventDamageEnabled();
        DamageCauseRule rule = vanillaEventDamage ? null : config.damageCauseRule(event.getCause().name());
        if (!vanillaEventDamage && rule == null) {
            if (debugHandler.shouldDebugCombat(attacker, target, null)) {
                debugHandler.debugCombat(attacker, target, null, "ENVIRONMENT_IGNORED", "combat_debug.environment_ignored", Map.of(
                        "cause", event.getCause().name()
                ));
            }
            return false;
        }
        DamageContextVariables.Builder context = CombatSupport.baseContext(event, target).toBuilder();
        if (!vanillaEventDamage && rule != null && rule.context() != null && !rule.context().isEmpty()) {
            context.putAll(rule.context());
        }
        double sourceDamage = event.getDamage();
        double baseDamage = vanillaEventDamage || rule == null ? sourceDamage : rule.resolveDamage(sourceDamage);
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            applyFallDamageContext(target, context, sourceDamage);
        }
        context.put("cause", event.getCause().name());
        context.put("damage_cause", event.getCause().name());
        context.put("damage_cause_id", event.getCause().name());
        context.put("base_damage", baseDamage);
        context.put("source_damage", sourceDamage);
        context.put("input_damage", sourceDamage);
        context.put("final_damage", event.getFinalDamage());
        context.put("target_uuid", target.getUniqueId().toString());
        context.put("target_type", target.getType().name());
        String damageTypeId = vanillaEventDamage
                ? config.vanillaEventDamageType()
                : (rule.hasDamageType() ? rule.damageTypeId() : config.defaultDamageType());
        DamageContextVariables resolvedContext = context.build();
        if (debugHandler.shouldDebugCombat(attacker, target, null)) {
            debugHandler.debugCombat(attacker, target, null, "ENVIRONMENT_RESOLVED", "combat_debug.environment_mapped", Map.of(
                    "attacker", debugHandler.describeEntity(attacker),
                    "target", debugHandler.describeEntity(target),
                    "cause", event.getCause().name(),
                    "damage_type", damageTypeId,
                    "source_damage", debugHandler.formatNumber(sourceDamage),
                    "base_damage", debugHandler.formatNumber(baseDamage)
            ));
        }
        DamageContext damageContext = attributeService.createDamageContext(
                attacker,
                target,
                null,
                event.getCause(),
                damageTypeId,
                sourceDamage,
                baseDamage,
                resolvedContext
        );
        if (vanillaEventDamage) {
            applyPerfectTakeover(event, damageContext, attacker, target, null, attacker);
            return true;
        }
        event.setCancelled(true);
        resolveAndApplyDamage(
                attributeService.resolveDamageApplicationAsync(damageContext),
                attacker,
                target,
                null,
                attacker,
                event.getFinalDamage(),
                "ENVIRONMENT_RESOLVE_EMPTY",
                "combat_debug.environment_resolve_empty",
                "ENVIRONMENT_ASYNC_RESOLVED",
                "combat_debug.environment_resolved_async",
                "ENVIRONMENT_APPLY",
                "combat_debug.environment_apply"
        );
        return true;
    }

    private void applyPerfectTakeover(EntityDamageEvent event,
            DamageContext damageContext,
            LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            Entity visualSource) {
        if (attributeService.perfectTakeoverCoordinator().isClaimed(event)) {
            return;
        }
        ResolvedDamage resolvedDamage = attributeService.resolveDamageApplication(damageContext);
        if (resolvedDamage == null || resolvedDamage.finalDamage() <= 0D) {
            event.setCancelled(true);
            if (debugHandler.shouldDebugCombat(attacker, target, projectile)) {
                debugHandler.debugCombat(attacker, target, projectile, "PERFECT_TAKEOVER_CANCELLED", "combat_debug.perfect_takeover_cancelled", Map.of(
                        "cause", event.getCause().name()
                ));
            }
            return;
        }
        var invulnerabilityGate = attributeService.attackBatchInvulnerabilityGate();
        boolean bypassInvulnerability = invulnerabilityGate.shouldBypass(target, damageContext);
        attributeService.perfectTakeoverCoordinator().claimAndApply(
                event,
                resolvedDamage,
                visualSource,
                bypassInvulnerability);
        if (bypassInvulnerability && debugHandler.shouldDebugCombat(attacker, target, projectile)) {
            debugHandler.debugCombat(attacker, target, projectile, "INVULNERABILITY_BYPASSED", "combat_debug.invulnerability_bypassed", Map.of(
                    "target", debugHandler.describeEntity(target),
                    "reason", "same_attacker_snapshot_and_damage_type",
                    "batch_signature", invulnerabilityGate.batchKey(damageContext),
                    "window_ms", invulnerabilityGate.windowMs()
            ));
        }
        if (debugHandler.shouldDebugCombat(attacker, target, projectile)) {
            debugHandler.debugCombat(attacker, target, projectile, "PERFECT_TAKEOVER_APPLIED", "combat_debug.perfect_takeover_applied", Map.of(
                    "cause", event.getCause().name(),
                    "final_damage", debugHandler.formatNumber(resolvedDamage.finalDamage())
            ));
        }
    }

    private void applyFallDamageContext(LivingEntity target, DamageContextVariables.Builder context, double vanillaDamage) {
        if (target == null || context == null) {
            return;
        }
        double fallDistance = Math.max(0D, target.getFallDistance());
        PotionEffect jumpBoost = target.getPotionEffect(PotionEffectType.JUMP_BOOST);
        int jumpBoostLevel = jumpBoost == null ? 0 : Math.max(0, jumpBoost.getAmplifier() + 1);
        double fallDamageFormula = Math.max(0D, Math.ceil(fallDistance - 3D - jumpBoostLevel));
        context.put("fall_distance", fallDistance);
        context.put("jump_boost_level", jumpBoostLevel);
        context.put("vanilla_fall_damage", vanillaDamage);
        context.put("fall_damage_formula", fallDamageFormula);
    }

    private boolean isMeleeAttackCoolingDown(Player player, LivingEntity attacker, LivingEntity target) {
        if (player == null || !attributeService.isAttackCoolingDown(player)) {
            return false;
        }
        if (debugHandler.shouldDebugCombat(attacker, target, null)) {
            debugHandler.debugCombat(attacker, target, null, "MELEE_HIT_BLOCKED", "combat_debug.melee_hit_blocked", Map.of(
                    "attacker", debugHandler.describeEntity(attacker)
            ));
        }
        return true;
    }

    private DamageContext createMeleeDamageContext(EntityDamageByEntityEvent event,
            LivingEntity attacker,
            LivingEntity target,
            DamageContextVariables context) {
        if (target == null) {
            return null;
        }
        return attributeService.createDamageContext(
                attacker,
                target,
                null,
                event.getCause(),
                null,
                event.getDamage(),
                0D,
                context
        );
    }

    private DamageContext createProjectileDamageContext(EntityDamageByEntityEvent event,
            Projectile projectile,
            LivingEntity target,
            DamageContextVariables context) {
        if (projectile == null || target == null) {
            return null;
        }
        LivingEntity shooter = projectile.getShooter() instanceof LivingEntity livingEntity ? livingEntity : null;
        var snapshot = attributeService.readProjectileSnapshot(projectile);
        if (snapshot == null) {
            if (debugHandler.shouldDebugCombat(shooter, target, projectile)) {
                debugHandler.debugCombat(shooter, target, projectile, "PROJECTILE_SNAPSHOT_MISSING", "combat_debug.projectile_snapshot_missing_strict", Map.of(
                        "projectile", debugHandler.describeEntity(projectile)
                ));
            }
            return null;
        }
        var attackerSnapshot = snapshot.attackSnapshot();
        var targetSnapshot = attributeService.collectCombatSnapshot(target);
        String damageTypeId = snapshot.damageTypeId();
        double sourceDamage = event.getDamage();
        double baseDamage = attributeService.config().vanillaEventDamageEnabled() ? sourceDamage : 0D;
        return attributeService.createDamageContext(
                shooter,
                target,
                projectile,
                event.getCause(),
                damageTypeId,
                sourceDamage,
                baseDamage,
                attackerSnapshot,
                targetSnapshot,
                context
        );
    }

    private void resolveAndApplyDamage(CompletableFuture<ResolvedDamage> future,
            LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            Entity visualSource,
            double fallbackDamage,
            String emptyPhase,
            String emptyMessageKey,
            String resolvedPhase,
            String resolvedMessageKey,
            String applyPhase,
            String applyMessageKey) {
        if (future == null) {
            debugHandler.debugCombat(attacker, target, projectile, emptyPhase, emptyMessageKey);
            applyFallbackDamage(target, fallbackDamage);
            return;
        }
        future.thenCompose(resolvedDamage -> {
            if (resolvedDamage == null) {
                debugHandler.debugCombat(attacker, target, projectile, emptyPhase, emptyMessageKey);
                return applyFallbackDamage(target, fallbackDamage);
            }
            debugHandler.debugCombat(attacker, target, projectile, resolvedPhase, resolvedMessageKey, Map.of(
                    "resolved", debugHandler.describeResolvedDamage(resolvedDamage)
            ));
            debugHandler.debugCombat(attacker, target, projectile, applyPhase, applyMessageKey);
            return attributeService.applyResolvedDamageAsync(resolvedDamage, visualSource, 0D);
        }).whenComplete((applied, throwable) -> {
            if (throwable != null) {
                debugHandler.debugCombat(attacker, target, projectile, "ASYNC_DAMAGE_FAILED", "combat_debug.async_damage_failed", Map.of(
                        "error", CombatSupport.rootCauseMessage(throwable)
                ));
                applyFallbackDamage(target, fallbackDamage);
            }
        });
    }

    private CompletableFuture<Boolean> applyFallbackDamage(LivingEntity target, double damage) {
        return CombatSupport.applyFallbackDamage(plugin, scheduling, target, damage, "Fallback");
    }
}
