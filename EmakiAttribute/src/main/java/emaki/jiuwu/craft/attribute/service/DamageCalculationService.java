package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeDamageEvent;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageStageSource;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.attribute.model.ProjectileDamageSnapshot;
import emaki.jiuwu.craft.attribute.model.RecoveryDefinition;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

final class DamageCalculationService {

    private static final double ZERO_EPSILON = 1.0E-9D;
    private final AttributeService service;

    DamageCalculationService(AttributeService service) {
        this.service = service;
    }

    public String defaultDamageTypeId() {
        if (service.config().defaultDamageType() != null && !service.config().defaultDamageType().isBlank()) {
            DamageTypeDefinition definition = service.damageTypeRegistry().resolve(service.config().defaultDamageType());
            if (definition != null) {
                return definition.id();
            }
        }
        if (!service.damageTypeRegistry().all().isEmpty()) {
            return service.damageTypeRegistry().all().values().iterator().next().id();
        }
        return "physical";
    }

    public String defaultProjectileDamageTypeId() {
        DamageTypeDefinition projectile = service.damageTypeRegistry().resolve("projectile");
        if (projectile != null) {
            return projectile.id();
        }
        return defaultDamageTypeId();
    }

    public DamageTypeDefinition resolveDamageType(String damageTypeId) {
        String resolvedId = Texts.isBlank(damageTypeId) ? defaultDamageTypeId() : normalizeId(damageTypeId);
        DamageTypeDefinition definition = service.damageTypeRegistry().resolve(resolvedId);
        if (definition != null) {
            return definition;
        }
        return new DamageTypeDefinition(resolvedId, resolvedId, List.of(), Set.of(), false, List.of(), null);
    }

    public void setDamageTypeOverride(LivingEntity entity, String damageTypeId) {
        service.stateRepository().setDamageTypeOverride(entity, damageTypeId);
    }

    public String peekDamageTypeOverride(LivingEntity entity) {
        return service.stateRepository().peekDamageTypeOverride(entity);
    }

    public String consumeDamageTypeOverride(LivingEntity entity) {
        return service.stateRepository().consumeDamageTypeOverride(entity);
    }

    public void markSyntheticDamage(LivingEntity entity, boolean value) {
        service.stateRepository().markSyntheticDamage(entity, value);
    }

    public boolean isSyntheticDamage(LivingEntity entity) {
        return service.stateRepository().isSyntheticDamage(entity);
    }

    public ProjectileDamageSnapshot snapshotProjectile(Projectile projectile, LivingEntity shooter) {
        if (projectile == null || shooter == null) {
            return null;
        }
        AttributeSnapshot attackSnapshot = service.collectCombatSnapshot(shooter);
        String damageTypeId = consumeDamageTypeOverride(shooter);
        if (Texts.isBlank(damageTypeId)) {
            damageTypeId = defaultProjectileDamageTypeId();
        }
        long now = System.currentTimeMillis();
        String sourceSignature = SignatureUtil.combine(
                attackSnapshot.sourceSignature(),
                damageTypeId,
                projectile.getUniqueId().toString(),
                shooter.getUniqueId().toString()
        );
        ProjectileDamageSnapshot snapshot = new ProjectileDamageSnapshot(
                ProjectileDamageSnapshot.CURRENT_SCHEMA_VERSION,
                normalizeId(damageTypeId),
                shooter.getUniqueId(),
                sourceSignature,
                now,
                now + service.projectileTtlMs(),
                attackSnapshot
        );
        service.stateRepository().writeProjectileSnapshot(projectile, snapshot);
        if (shouldDebugCombat(shooter, null, projectile)) {
            debugCombat(shooter, null, projectile, "PROJECTILE_SNAPSHOT_WRITE", "combat_debug.projectile_snapshot_write", Map.of(
                    "projectile", entityDebugLabel(projectile),
                    "shooter", entityDebugLabel(shooter),
                    "damage_type", snapshot.damageTypeId(),
                    "signature", snapshot.sourceSignature(),
                    "snapshot", formatSnapshot(snapshot.attackSnapshot())
            ));
        }
        return snapshot;
    }

    public ProjectileDamageSnapshot readProjectileSnapshot(Projectile projectile) {
        return service.stateRepository().readProjectileSnapshot(projectile);
    }

    public DamageContext createDamageContext(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId,
            double sourceDamage,
            double baseDamage,
            AttributeSnapshot attackerSnapshot,
            AttributeSnapshot targetSnapshot,
            DamageContextVariables context) {
        String resolvedDamageTypeId = resolveRequestedDamageTypeId(attacker, projectile, cause, damageTypeId);
        DamageTypeDefinition damageType = resolveDamageType(resolvedDamageTypeId);
        AttributeSnapshot resolvedAttacker = attackerSnapshot;
        if (resolvedAttacker == null) {
            resolvedAttacker = attacker == null ? AttributeSnapshot.empty("") : service.collectCombatSnapshot(attacker);
        }
        AttributeSnapshot resolvedTarget = targetSnapshot;
        if (resolvedTarget == null) {
            resolvedTarget = target == null ? AttributeSnapshot.empty("") : service.collectCombatSnapshot(target);
        }
        DamageContextVariables.Builder merged = DamageContextVariables.builder();
        merged.putAll(context);
        String attackerLabel = entityLabel(attacker, cause, messageOrFallback("damage.environment", "environment"));
        String targetLabel = entityLabel(target, null, messageOrFallback("damage.target", "target"));
        merged.put("attacker", attackerLabel);
        merged.put("attacker_name", attackerLabel);
        merged.put("attacker_type", attacker == null ? "" : attacker.getType().name());
        merged.put("attacker_uuid", attacker == null ? "" : attacker.getUniqueId().toString());
        merged.put("source", attackerLabel);
        merged.put("source_name", attackerLabel);
        merged.put("target", targetLabel);
        merged.put("target_name", targetLabel);
        merged.put("target_type", target == null ? "" : target.getType().name());
        merged.put("target_uuid", target == null ? "" : target.getUniqueId().toString());
        merged.put("damage_type", damageType.displayName());
        merged.put("damage_type_name", damageType.displayName());
        merged.put("damage_type_id", damageType.id());
        merged.put("source_damage", sourceDamage);
        merged.put("input_damage", sourceDamage);
        merged.put("base_damage", baseDamage);
        merged.put("damage", baseDamage);
        merged.put("cause", cause == null ? "" : cause.name());
        merged.put("cause_name", causeDisplayName(cause));
        merged.put("cause_id", cause == null ? "" : normalizeId(cause.name()));
        merged.put("damage_cause", cause == null ? "" : cause.name());
        merged.put("damage_cause_name", causeDisplayName(cause));
        merged.put("damage_cause_id", cause == null ? "" : normalizeId(cause.name()));
        if (projectile != null) {
            merged.put("projectile_type", projectile.getType().name());
            merged.put("projectile_uuid", projectile.getUniqueId().toString());
        }
        return DamageContext.of(
                attacker,
                target,
                projectile,
                cause,
                resolvedDamageTypeId,
                sourceDamage,
                baseDamage,
                resolvedAttacker,
                resolvedTarget,
                merged.build()
        );
    }

    public DamageContext createDamageContext(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId,
            double sourceDamage,
            double baseDamage,
            Map<String, ?> context) {
        return createDamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, null, null, DamageContextVariables.from(context));
    }

    public DamageContext createDamageContext(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId,
            double sourceDamage,
            double baseDamage,
            DamageContextVariables context) {
        return createDamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, null, null, context);
    }

    public DamageResult calculateDamage(DamageContext damageContext) {
        DamageCalculationPlan plan = prepareDamageCalculation(damageContext);
        if (plan == null) {
            return new DamageResult("", 0D, false, 0D, Map.of(), DamageContext.legacy("", 0D, null, null, DamageContextVariables.empty()));
        }
        return service.damageEngine().resolve(plan.request(), plan.damageType(), plan.seededRoll());
    }

    public DamageResult calculateDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            DamageContextVariables context) {
        double sourceDamage = contextDouble(context, "source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = extractDamageCause(context);
        DamageContext damageContext = createDamageContext(attacker, target, null, cause, damageTypeId, sourceDamage, baseDamage, context);
        return calculateDamage(damageContext);
    }

    public DamageResult calculateDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, ?> context) {
        return calculateDamage(attacker, target, damageTypeId, baseDamage, DamageContextVariables.from(context));
    }

    public boolean applyDamage(DamageContext damageContext) {
        if (damageContext.projectile() != null) {
            return applyProjectileDamage(damageContext);
        }
        return applyResolvedDamage(resolveDamageApplication(damageContext), damageContext == null ? null : damageContext.attacker(), 0D);
    }

    public boolean applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            DamageContextVariables context) {
        double sourceDamage = contextDouble(context, "source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = extractDamageCause(context);
        DamageContext damageContext = createDamageContext(attacker, target, null, cause, damageTypeId, sourceDamage, baseDamage, context);
        return applyDamage(damageContext);
    }

    public boolean applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, ?> context) {
        return applyDamage(attacker, target, damageTypeId, baseDamage, DamageContextVariables.from(context));
    }

    public boolean applyProjectileDamage(DamageContext damageContext) {
        if (damageContext == null || damageContext.projectile() == null || damageContext.target() == null) {
            return false;
        }
        Projectile projectile = damageContext.projectile();
        ProjectileDamageSnapshot snapshot = readProjectileSnapshot(projectile);
        if (snapshot == null) {
            if (shouldDebugCombat(damageContext)) {
                debugCombat(damageContext, "PROJECTILE_SNAPSHOT_MISSING", "combat_debug.projectile_snapshot_missing_apply", Map.of(
                        "projectile", entityDebugLabel(projectile)
                ));
            }
            return false;
        }
        AttributeSnapshot attackSnapshot = snapshot.attackSnapshot() == null ? AttributeSnapshot.empty("") : snapshot.attackSnapshot();
        AttributeSnapshot targetSnapshot = service.collectCombatSnapshot(damageContext.target());
        LivingEntity shooter = projectile.getShooter() instanceof LivingEntity livingEntity ? livingEntity : damageContext.attacker();
        DamageContext resolvedContext = createDamageContext(
                shooter,
                damageContext.target(),
                projectile,
                damageContext.cause(),
                snapshot.damageTypeId(),
                damageContext.sourceDamage(),
                damageContext.baseDamage(),
                attackSnapshot,
                targetSnapshot,
                damageContext.variables()
        );
        return applyResolvedDamage(resolveDamageApplication(resolvedContext), projectile, 0D);
    }

    public boolean applyProjectileDamage(Projectile projectile,
            LivingEntity target,
            double baseDamage,
            DamageContextVariables context) {
        if (projectile == null || target == null) {
            return false;
        }
        LivingEntity shooter = projectile.getShooter() instanceof LivingEntity livingEntity ? livingEntity : null;
        double sourceDamage = contextDouble(context, "source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = extractDamageCause(context);
        DamageContext damageContext = createDamageContext(shooter, target, projectile, cause, defaultProjectileDamageTypeId(), sourceDamage, baseDamage, context);
        return applyProjectileDamage(damageContext);
    }

    public boolean applyProjectileDamage(Projectile projectile,
            LivingEntity target,
            double baseDamage,
            Map<String, ?> context) {
        return applyProjectileDamage(projectile, target, baseDamage, DamageContextVariables.from(context));
    }

    public void clearPlayerDamageTypeOverride(Player player) {
        service.stateRepository().setDamageTypeOverride(player, null);
    }

    public ResolvedDamage resolveDamageApplication(DamageContext damageContext) {
        if (!isResolvableDamageContext(damageContext)) {
            debugCombat(damageContext, "RESOLVE_SKIPPED", "combat_debug.resolve_skipped");
            return null;
        }
        if (shouldDebugCombat(damageContext)) {
            debugCombat(damageContext, "RESOLVE_BEGIN", "combat_debug.resolve_begin_sync", Map.of(
                    "context", describeDamageContext(damageContext),
                    "attacker_snapshot", formatSnapshot(damageContext.attackerSnapshot()),
                    "target_snapshot", formatSnapshot(damageContext.targetSnapshot())
            ));
        }
        DamageCalculationPlan plan = prepareDamageCalculation(damageContext);
        if (plan == null) {
            return null;
        }
        DamageResult result = service.damageEngine().resolve(plan.request(), plan.damageType(), plan.seededRoll());
        return finalizeResolvedDamage(plan.damageContext(), result);
    }

    public CompletableFuture<ResolvedDamage> resolveDamageApplicationAsync(DamageContext damageContext) {
        if (!isResolvableDamageContext(damageContext)) {
            debugCombat(damageContext, "RESOLVE_SKIPPED", "combat_debug.resolve_skipped");
            return CompletableFuture.completedFuture(null);
        }
        if (shouldDebugCombat(damageContext)) {
            debugCombat(damageContext, "RESOLVE_BEGIN", "combat_debug.resolve_begin_async", Map.of(
                    "context", describeDamageContext(damageContext),
                    "attacker_snapshot", formatSnapshot(damageContext.attackerSnapshot()),
                    "target_snapshot", formatSnapshot(damageContext.targetSnapshot())
            ));
        }
        DamageCalculationPlan plan = prepareDamageCalculation(damageContext);
        if (plan == null) {
            return CompletableFuture.completedFuture(null);
        }
        return service.asyncDamageEngine()
                .resolveAsync(plan.request(), plan.damageType(), plan.seededRoll())
                .thenCompose(result -> {
                    if (service.asyncTaskScheduler() == null) {
                        return CompletableFuture.completedFuture(finalizeResolvedDamage(plan.damageContext(), result));
                    }
                    return service.asyncTaskScheduler().callSync(
                            "attribute-damage-finalize",
                            () -> finalizeResolvedDamage(plan.damageContext(), result)
                    );
                });
    }

    public boolean applyResolvedDamage(ResolvedDamage resolvedDamage, Entity visualSource, double alreadyAppliedDamage) {
        if (resolvedDamage == null || resolvedDamage.damageContext() == null) {
            return false;
        }
        ResolvedDamage effectiveResolvedDamage = shouldTriggerMythicOnDamaged(resolvedDamage.damageContext())
                ? dispatchSyntheticOnDamaged(resolvedDamage, visualSource)
                : resolvedDamage;
        if (effectiveResolvedDamage == null || effectiveResolvedDamage.damageContext() == null) {
            return false;
        }
        DamageContext damageContext = effectiveResolvedDamage.damageContext();
        LivingEntity target = damageContext.target();
        if (target == null || !target.isValid() || target.isDead()) {
            debugCombat(damageContext, "APPLY_SKIPPED", "combat_debug.apply_skipped");
            return false;
        }
        double appliedDamage = Math.max(0D, alreadyAppliedDamage);
        double remainingDamage = Math.max(0D, effectiveResolvedDamage.finalDamage() - appliedDamage);
        double healthBefore = target.getHealth();
        double absorptionBefore = target.getAbsorptionAmount();
        if (shouldDebugCombat(damageContext)) {
            debugCombat(damageContext, "APPLY_BEGIN", "combat_debug.apply_begin", Map.of(
                    "final_damage", formatNumber(effectiveResolvedDamage.finalDamage()),
                    "already_applied", formatNumber(appliedDamage),
                    "remaining", formatNumber(remainingDamage),
                    "target_health_before", formatNumber(healthBefore),
                    "target_absorption_before", formatNumber(absorptionBefore),
                    "visual_source", entityDebugLabel(visualSource)
            ));
        }
        applyDirectDamage(target, remainingDamage, visualSource);
        applyAggroTarget(target, damageContext.attacker());
        int cooldownTicks = 0;
        if (damageContext.attacker() instanceof Player player) {
            cooldownTicks = service.startAttackCooldown(player, damageContext.attackerSnapshot(), player.getInventory().getItemInMainHand());
        }
        applyRecovery(damageContext, effectiveResolvedDamage.damageType(), effectiveResolvedDamage.damageResult(), effectiveResolvedDamage.finalDamage());
        notifyDamageMessages(damageContext, effectiveResolvedDamage.damageType(), effectiveResolvedDamage.damageResult(), effectiveResolvedDamage.finalDamage());
        service.scheduleHealthSync(target);
        if (shouldDebugCombat(damageContext)) {
            debugCombat(damageContext, "APPLY_DONE", "combat_debug.apply_done", Map.of(
                    "target_health_after", formatNumber(target.getHealth()),
                    "target_absorption_after", formatNumber(target.getAbsorptionAmount()),
                    "attacker_cooldown_ticks", cooldownTicks
            ));
        }
        return remainingDamage > 0D || appliedDamage > 0D;
    }

    private void applyAggroTarget(LivingEntity target, LivingEntity attacker) {
        if (!(target instanceof Mob mob) || attacker == null || !attacker.isValid() || attacker.isDead()
                || !target.isValid() || target.isDead() || target.getUniqueId().equals(attacker.getUniqueId())) {
            return;
        }
        mob.setAware(true);
        mob.setTarget(attacker);
    }

    private void applyDirectDamage(LivingEntity target, double damage, Entity visualSource) {
        if (target == null || damage <= 0D || !target.isValid() || target.isDead()) {
            return;
        }
        target.setNoDamageTicks(0);
        double remainingDamage = damage;
        double absorption = Math.max(0D, target.getAbsorptionAmount());
        if (absorption > 0D) {
            double absorbed = Math.min(absorption, remainingDamage);
            target.setAbsorptionAmount(Math.max(0D, absorption - absorbed));
            remainingDamage -= absorbed;
        }
        target.setLastDamage(damage);
        if (remainingDamage <= 0D) {
            playSyntheticImpact(target, visualSource);
            return;
        }
        double currentHealth = Math.max(0D, target.getHealth());
        double nextHealth = Math.max(0D, currentHealth - remainingDamage);
        target.setHealth(nextHealth);
        if (nextHealth > 0D) {
            playSyntheticImpact(target, visualSource);
        }
    }

    private void playSyntheticImpact(LivingEntity target, Entity visualSource) {
        if (target == null || !target.isValid() || target.isDead()) {
            return;
        }
        float yaw = visualSource == null ? target.getLocation().getYaw() : visualSource.getLocation().getYaw();
        target.playHurtAnimation(yaw);
        if (service.config().syntheticHitHurtSound() && target.getHurtSound() != null) {
            target.getWorld().playSound(target, target.getHurtSound(), 1F, 1F);
        }
    }

    private String resolveRequestedDamageTypeId(LivingEntity attacker,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId) {
        if (Texts.isNotBlank(damageTypeId)) {
            return normalizeId(damageTypeId);
        }
        if (attacker != null) {
            String override = consumeDamageTypeOverride(attacker);
            if (Texts.isNotBlank(override)) {
                return normalizeId(override);
            }
        }
        if (projectile != null || cause == EntityDamageEvent.DamageCause.PROJECTILE) {
            return defaultProjectileDamageTypeId();
        }
        return defaultDamageTypeId();
    }

    private boolean isResolvableDamageContext(DamageContext damageContext) {
        return damageContext != null
                && damageContext.target() != null
                && damageContext.target().isValid()
                && !damageContext.target().isDead();
    }

    private DamageCalculationPlan prepareDamageCalculation(DamageContext damageContext) {
        if (damageContext == null) {
            return null;
        }
        String resolvedTypeId = damageContext.damageTypeId();
        if (Texts.isBlank(resolvedTypeId)) {
            resolvedTypeId = defaultDamageTypeId();
        }
        DamageTypeDefinition damageType = resolveDamageType(resolvedTypeId);
        DamageContext resolvedContext = damageContext.withDamageTypeId(damageType.id());
        boolean allowCritical = contextBoolean(resolvedContext.variables(), true, "allow_critical", "critical");
        boolean allowTargetDodge = contextBoolean(resolvedContext.variables(), false, "allow_target_dodge", "target_dodge", "allow_dodge", "dodge");
        boolean calculateTargetDefense = contextBoolean(
                resolvedContext.variables(),
                true,
                "calculate_target_defense",
                "target_defense",
                "calculate_defense",
                "defense"
        );
        DamageContextVariables.Builder variables = resolvedContext.variables().toBuilder();
        variables.put("allow_critical", allowCritical);
        variables.put("allow_target_dodge", allowTargetDodge);
        variables.put("calculate_target_defense", calculateTargetDefense);
        resolvedContext = resolvedContext.withVariables(variables.build());
        DamageTypeDefinition effectiveDamageType = filterDamageType(damageType, allowCritical, calculateTargetDefense);
        double seededRoll = ThreadLocalRandom.current().nextDouble(0D, 100D);
        if (allowTargetDodge) {
            double dodgeChance = Math.max(0D, Math.min(100D, readSnapshotAttribute(resolvedContext.targetSnapshot(), "dodge_chance")));
            double dodgeRoll = ThreadLocalRandom.current().nextDouble(0D, 100D);
            resolvedContext = resolvedContext.withVariables(resolvedContext.variables().toBuilder()
                    .put("dodge_chance", dodgeChance)
                    .put("dodge_roll", dodgeRoll)
                    .put("dodged", dodgeRoll < dodgeChance)
                    .build());
            if (dodgeRoll < dodgeChance) {
                if (shouldDebugCombat(resolvedContext)) {
                    debugCombat(resolvedContext, "DODGE_TRIGGERED", "combat_debug.dodge_triggered", Map.of(
                            "dodge_chance", formatNumber(dodgeChance),
                            "dodge_roll", formatNumber(dodgeRoll)
                    ));
                }
                return new DamageCalculationPlan(
                        resolvedContext.withBaseDamage(0D),
                        emptyDamageType(damageType),
                        new DamageRequest(resolvedContext.withBaseDamage(0D)),
                        seededRoll
                );
            }
        }
        return new DamageCalculationPlan(
                resolvedContext,
                effectiveDamageType,
                new DamageRequest(resolvedContext),
                seededRoll
        );
    }

    private ResolvedDamage finalizeResolvedDamage(DamageContext damageContext, DamageResult result) {
        if (!isResolvableDamageContext(damageContext) || result == null) {
            debugCombat(damageContext, "EVENT_BLOCKED", "combat_debug.event_result_empty");
            return null;
        }
        if (shouldDebugCombat(damageContext)) {
            debugCombat(damageContext, "CALC_RESULT", "combat_debug.calc_result", Map.of(
                    "damage_type", result.damageTypeId(),
                    "final_damage", formatNumber(result.finalDamage()),
                    "critical", result.critical(),
                    "roll", formatNumber(result.roll()),
                    "stages", formatStageValues(result.stageValues())
            ));
        }
        EmakiAttributeDamageEvent event = new EmakiAttributeDamageEvent(damageContext, result);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getFinalDamage() <= 0D) {
            if (shouldDebugCombat(damageContext)) {
                debugCombat(damageContext, "EVENT_BLOCKED", "combat_debug.event_blocked", Map.of(
                        "cancelled", event.isCancelled(),
                        "final_damage", formatNumber(event.getFinalDamage())
                ));
            }
            return null;
        }
        DamageTypeDefinition damageType = resolveDamageType(result.damageTypeId());
        if (shouldDebugCombat(damageContext)) {
            debugCombat(damageContext, "EVENT_PASSED", "combat_debug.event_passed", Map.of(
                    "final_damage", formatNumber(event.getFinalDamage()),
                    "resolved_damage_type", damageType.id()
            ));
        }
        return new ResolvedDamage(damageContext, result, damageType, event.getFinalDamage());
    }

    private boolean shouldTriggerMythicOnDamaged(DamageContext damageContext) {
        return damageContext != null
                && contextBoolean(
                        damageContext.variables(),
                        false,
                        "trigger_mythic_on_damaged",
                        "trigger_on_damaged",
                        "mythic_on_damaged"
                );
    }

    private ResolvedDamage dispatchSyntheticOnDamaged(ResolvedDamage resolvedDamage, Entity visualSource) {
        if (resolvedDamage == null || resolvedDamage.damageContext() == null) {
            return null;
        }
        DamageContext damageContext = resolvedDamage.damageContext();
        LivingEntity target = damageContext.target();
        if (target == null || !target.isValid() || target.isDead()) {
            return null;
        }
        EntityDamageEvent.DamageCause cause = damageContext.cause() == null ? EntityDamageEvent.DamageCause.CUSTOM : damageContext.cause();
        DamageSource damageSource = buildSyntheticDamageSource(damageContext, visualSource, cause);
        EntityDamageEvent event = visualSource == null
                ? new EntityDamageEvent(target, cause, damageSource, resolvedDamage.finalDamage())
                : new EntityDamageByEntityEvent(visualSource, target, cause, damageSource, resolvedDamage.finalDamage());
        markSyntheticDamage(target, true);
        try {
            Bukkit.getPluginManager().callEvent(event);
        } finally {
            markSyntheticDamage(target, false);
        }
        if (event.isCancelled() || event.getFinalDamage() <= 0D) {
            if (shouldDebugCombat(damageContext)) {
                debugCombat(damageContext, "MYTHIC_ON_DAMAGED_BLOCKED", "combat_debug.mythic_on_damaged_blocked", Map.of(
                        "cancelled", event.isCancelled(),
                        "final_damage", formatNumber(event.getFinalDamage())
                ));
            }
            return null;
        }
        if (Math.abs(event.getFinalDamage() - resolvedDamage.finalDamage()) > ZERO_EPSILON) {
            if (shouldDebugCombat(damageContext)) {
                debugCombat(damageContext, "MYTHIC_ON_DAMAGED_ADJUSTED", "combat_debug.mythic_on_damaged_adjusted", Map.of(
                        "before", formatNumber(resolvedDamage.finalDamage()),
                        "after", formatNumber(event.getFinalDamage())
                ));
            }
        }
        return new ResolvedDamage(
                damageContext,
                resolvedDamage.damageResult(),
                resolvedDamage.damageType(),
                Math.max(0D, event.getFinalDamage())
        );
    }

    private DamageSource buildSyntheticDamageSource(DamageContext damageContext, Entity visualSource, EntityDamageEvent.DamageCause cause) {
        DamageSource.Builder builder = DamageSource.builder(resolveSyntheticDamageType(damageContext, visualSource, cause));
        Entity directEntity = resolveDirectDamageEntity(damageContext, visualSource);
        Entity causingEntity = resolveCausingDamageEntity(damageContext, visualSource);
        if (directEntity != null && directEntity.isValid()) {
            builder.withDirectEntity(directEntity);
            builder.withDamageLocation(directEntity.getLocation());
        } else if (damageContext != null && damageContext.target() != null) {
            builder.withDamageLocation(damageContext.target().getLocation());
        }
        if (causingEntity != null && causingEntity.isValid()) {
            builder.withCausingEntity(causingEntity);
            if (directEntity == null) {
                builder.withDamageLocation(causingEntity.getLocation());
            }
        }
        return builder.build();
    }

    private DamageType resolveSyntheticDamageType(DamageContext damageContext, Entity visualSource, EntityDamageEvent.DamageCause cause) {
        Entity source = visualSource != null ? visualSource : resolveDirectDamageEntity(damageContext, null);
        return switch (cause == null ? EntityDamageEvent.DamageCause.CUSTOM : cause) {
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK ->
                damageContext != null && damageContext.attacker() instanceof Player ? DamageType.PLAYER_ATTACK : DamageType.MOB_ATTACK;
            case PROJECTILE -> resolveProjectileDamageType(source);
            case MAGIC, POISON -> source instanceof Projectile ? DamageType.INDIRECT_MAGIC : DamageType.MAGIC;
            case THORNS -> DamageType.THORNS;
            case BLOCK_EXPLOSION -> DamageType.EXPLOSION;
            case ENTITY_EXPLOSION ->
                source instanceof Player ? DamageType.PLAYER_EXPLOSION : DamageType.EXPLOSION;
            case SONIC_BOOM -> DamageType.SONIC_BOOM;
            case WITHER -> DamageType.WITHER;
            case FALL -> DamageType.FALL;
            case DROWNING -> DamageType.DROWN;
            case LAVA -> DamageType.LAVA;
            case FIRE_TICK -> DamageType.ON_FIRE;
            case FIRE -> DamageType.IN_FIRE;
            case CONTACT -> DamageType.CACTUS;
            case VOID -> DamageType.OUT_OF_WORLD;
            default -> DamageType.GENERIC;
        };
    }

    private DamageType resolveProjectileDamageType(Entity source) {
        if (source instanceof Trident) {
            return DamageType.TRIDENT;
        }
        if (source instanceof WitherSkull) {
            return DamageType.WITHER_SKULL;
        }
        if (source instanceof Fireball) {
            return DamageType.FIREBALL;
        }
        if (source instanceof AbstractArrow) {
            return DamageType.ARROW;
        }
        return DamageType.MOB_PROJECTILE;
    }

    private Entity resolveDirectDamageEntity(DamageContext damageContext, Entity visualSource) {
        if (visualSource != null) {
            return visualSource;
        }
        if (damageContext == null) {
            return null;
        }
        if (damageContext.projectile() != null) {
            return damageContext.projectile();
        }
        return damageContext.attacker();
    }

    private Entity resolveCausingDamageEntity(DamageContext damageContext, Entity visualSource) {
        if (damageContext != null && damageContext.attacker() != null) {
            return damageContext.attacker();
        }
        if (visualSource instanceof Projectile projectile && projectile.getShooter() instanceof Entity entity) {
            return entity;
        }
        return visualSource;
    }

    private DamageTypeDefinition filterDamageType(DamageTypeDefinition damageType, boolean allowCritical, boolean calculateTargetDefense) {
        if (damageType == null || (allowCritical && calculateTargetDefense)) {
            return damageType;
        }
        List<emaki.jiuwu.craft.attribute.model.DamageStageDefinition> filteredStages = new ArrayList<>();
        for (var stage : damageType.stages()) {
            if (stage == null) {
                continue;
            }
            if (!allowCritical && isCriticalStage(stage.id())) {
                continue;
            }
            if (!calculateTargetDefense && isDefenseStage(stage.id())) {
                continue;
            }
            filteredStages.add(stage);
        }
        return new DamageTypeDefinition(
                damageType.id(),
                damageType.displayName(),
                damageType.aliases(),
                damageType.allowedEvents(),
                damageType.hardLock(),
                filteredStages,
                damageType.recovery(),
                damageType.description(),
                damageType.attackerMessage(),
                damageType.targetMessage()
        );
    }

    private DamageTypeDefinition emptyDamageType(DamageTypeDefinition damageType) {
        if (damageType == null) {
            return new DamageTypeDefinition("", "", List.of(), Set.of(), false, List.of(), null);
        }
        return new DamageTypeDefinition(
                damageType.id(),
                damageType.displayName(),
                damageType.aliases(),
                damageType.allowedEvents(),
                damageType.hardLock(),
                List.of(),
                null,
                damageType.description(),
                damageType.attackerMessage(),
                damageType.targetMessage()
        );
    }

    private boolean isCriticalStage(String stageId) {
        String normalized = normalizeId(stageId);
        return "crit".equals(normalized) || "critical".equals(normalized);
    }

    private boolean isDefenseStage(String stageId) {
        String normalized = normalizeId(stageId);
        return "defense".equals(normalized) || "target_defense".equals(normalized);
    }

    private double readSnapshotAttribute(AttributeSnapshot snapshot, String attributeId) {
        if (snapshot == null || snapshot.values().isEmpty() || Texts.isBlank(attributeId)) {
            return 0D;
        }
        Double value = snapshot.values().get(normalizeId(attributeId));
        return value == null ? 0D : value;
    }

    private void applyRecovery(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageResult result,
            double finalDamage) {
        if (damageContext == null || damageType == null || !damageType.hasRecovery() || result == null) {
            return;
        }
        LivingEntity attacker = damageContext.attacker();
        if (attacker == null || !attacker.isValid() || attacker.isDead()) {
            return;
        }
        double recoveryAmount = resolveRecoveryAmount(damageContext, damageType.recovery(), finalDamage);
        if (recoveryAmount <= 0D) {
            return;
        }
        double currentHealth = Math.max(0D, attacker.getHealth());
        double maxHealth = Math.max(1D, attacker.getMaxHealth());
        attacker.setHealth(Math.min(maxHealth, currentHealth + recoveryAmount));
        service.scheduleHealthSync(attacker);
    }

    private void notifyDamageMessages(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageResult result,
            double finalDamage) {
        if (damageContext == null || damageType == null || result == null) {
            return;
        }
        Map<String, Object> replacements = buildDamageMessageReplacements(damageContext, damageType, result, finalDamage);
        Player attackerPlayer = damageContext.attacker() instanceof Player player ? player : null;
        Player targetPlayer = damageContext.target() instanceof Player player ? player : null;
        if (attackerPlayer != null && targetPlayer != null && attackerPlayer.getUniqueId().equals(targetPlayer.getUniqueId())) {
            sendDamageMessage(attackerPlayer, firstNonBlank(damageType.attackerMessage(), damageType.targetMessage()), replacements);
            return;
        }
        sendDamageMessage(attackerPlayer, damageType.attackerMessage(), replacements);
        sendDamageMessage(targetPlayer, damageType.targetMessage(), replacements);
    }

    private void sendDamageMessage(Player player, String template, Map<String, Object> replacements) {
        if (player == null || Texts.isBlank(template)) {
            return;
        }
        String rendered = Texts.formatTemplate(template, replacements);
        if (Texts.isBlank(rendered)) {
            return;
        }
        AdventureSupport.sendMessage(service.plugin(), player, MiniMessages.parse(rendered));
    }

    private Map<String, Object> buildDamageMessageReplacements(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageResult result,
            double finalDamage) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        String attackerLabel = entityLabel(damageContext.attacker(), damageContext.cause(), messageOrFallback("damage.environment", "environment"));
        String targetLabel = entityLabel(damageContext.target(), null, messageOrFallback("damage.target", "target"));
        String damageTypeLabel = Texts.isBlank(damageType.displayName()) ? damageType.id() : damageType.displayName();
        String sourceDamageText = Numbers.formatNumber(damageContext.sourceDamage(), "0.##");
        String baseDamageText = Numbers.formatNumber(damageContext.baseDamage(), "0.##");
        String finalDamageText = Numbers.formatNumber(finalDamage, "0.##");
        String causeName = causeDisplayName(damageContext.cause());
        replacements.put("attacker", attackerLabel);
        replacements.put("attacker_name", attackerLabel);
        replacements.put("attacker_type", damageContext.attacker() == null ? causeName : damageContext.attacker().getType().name());
        replacements.put("attacker_uuid", damageContext.attacker() == null ? "" : damageContext.attacker().getUniqueId().toString());
        replacements.put("source", attackerLabel);
        replacements.put("source_name", attackerLabel);
        replacements.put("source_type", damageContext.attacker() == null ? causeName : damageContext.attacker().getType().name());
        replacements.put("source_uuid", damageContext.attacker() == null ? "" : damageContext.attacker().getUniqueId().toString());
        replacements.put("target", targetLabel);
        replacements.put("target_name", targetLabel);
        replacements.put("target_type", damageContext.target() == null ? "" : damageContext.target().getType().name());
        replacements.put("target_uuid", damageContext.target() == null ? "" : damageContext.target().getUniqueId().toString());
        replacements.put("damage_type", damageTypeLabel);
        replacements.put("damage_type_name", damageTypeLabel);
        replacements.put("damage_type_id", damageType.id());
        replacements.put("source_damage", sourceDamageText);
        replacements.put("input_damage", sourceDamageText);
        replacements.put("base_damage", baseDamageText);
        replacements.put("final_damage", finalDamageText);
        replacements.put("damage", finalDamageText);
        replacements.put("cause", damageContext.causeName());
        replacements.put("cause_name", causeName);
        replacements.put("cause_id", damageContext.causeId());
        replacements.put("damage_cause", damageContext.causeName());
        replacements.put("damage_cause_name", causeName);
        replacements.put("damage_cause_id", damageContext.causeId());
        replacements.put("critical", result.critical());
        replacements.put("critical_text", result.critical() ? messageOrFallback("damage.critical_text", "critical") : "");
        replacements.put("critical_suffix", result.critical() ? messageOrFallback("damage.critical_suffix", " <red>critical</red>") : "");
        replacements.put("roll", Numbers.formatNumber(result.roll(), "0.##"));
        return replacements;
    }

    private String entityLabel(LivingEntity entity, EntityDamageEvent.DamageCause cause, String fallback) {
        if (entity == null) {
            return cause != null ? causeDisplayName(cause) : fallback;
        }
        String name = Texts.toStringSafe(entity.getName()).trim();
        if (Texts.isBlank(name)) {
            name = entity.getType().name();
        }
        return Texts.isBlank(name) ? fallback : name;
    }

    private String causeDisplayName(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return messageOrFallback("damage.cause.environment", "environment");
        }
        return switch (cause) {
            case CONTACT ->
                messageOrFallback("damage.cause.contact", "contact");
            case ENTITY_ATTACK ->
                messageOrFallback("damage.cause.entity_attack", "attack");
            case PROJECTILE ->
                messageOrFallback("damage.cause.projectile", "projectile");
            case SUFFOCATION ->
                messageOrFallback("damage.cause.suffocation", "suffocation");
            case FALL ->
                messageOrFallback("damage.cause.fall", "fall");
            case FIRE ->
                messageOrFallback("damage.cause.fire", "fire");
            case FIRE_TICK ->
                messageOrFallback("damage.cause.fire_tick", "burning");
            case MELTING ->
                messageOrFallback("damage.cause.melting", "melting");
            case LAVA ->
                messageOrFallback("damage.cause.lava", "lava");
            case DROWNING ->
                messageOrFallback("damage.cause.drowning", "drowning");
            case BLOCK_EXPLOSION ->
                messageOrFallback("damage.cause.block_explosion", "block explosion");
            case ENTITY_EXPLOSION ->
                messageOrFallback("damage.cause.entity_explosion", "entity explosion");
            case VOID ->
                messageOrFallback("damage.cause.void", "void");
            case LIGHTNING ->
                messageOrFallback("damage.cause.lightning", "lightning");
            case WORLD_BORDER ->
                messageOrFallback("damage.cause.world_border", "world border");
            case STARVATION ->
                messageOrFallback("damage.cause.starvation", "starvation");
            case POISON ->
                messageOrFallback("damage.cause.poison", "poison");
            case MAGIC ->
                messageOrFallback("damage.cause.magic", "magic");
            case WITHER ->
                messageOrFallback("damage.cause.wither", "wither");
            case FALLING_BLOCK ->
                messageOrFallback("damage.cause.falling_block", "falling block");
            case DRAGON_BREATH ->
                messageOrFallback("damage.cause.dragon_breath", "dragon breath");
            case FLY_INTO_WALL ->
                messageOrFallback("damage.cause.fly_into_wall", "collision");
            case HOT_FLOOR ->
                messageOrFallback("damage.cause.hot_floor", "hot floor");
            case CAMPFIRE ->
                messageOrFallback("damage.cause.campfire", "campfire");
            case CRAMMING ->
                messageOrFallback("damage.cause.cramming", "cramming");
            case DRYOUT ->
                messageOrFallback("damage.cause.dryout", "dryout");
            case FREEZE ->
                messageOrFallback("damage.cause.freeze", "freeze");
            case SONIC_BOOM ->
                messageOrFallback("damage.cause.sonic_boom", "sonic boom");
            default ->
                messageOrFallback("damage.cause.unknown", cause.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        };
    }

    private EntityDamageEvent.DamageCause extractDamageCause(DamageContextVariables context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        Object raw = context.get("cause");
        if (raw == null) {
            raw = context.get("damage_cause");
        }
        if (raw == null) {
            raw = context.get("damage_cause_id");
        }
        if (raw == null) {
            return null;
        }
        if (raw instanceof EntityDamageEvent.DamageCause cause) {
            return cause;
        }
        String normalized = normalizeId(String.valueOf(raw));
        if (Texts.isBlank(normalized)) {
            return null;
        }
        try {
            return EntityDamageEvent.DamageCause.valueOf(normalized.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private double contextDouble(DamageContextVariables context, String key, double fallback) {
        if (context == null || context.isEmpty() || Texts.isBlank(key)) {
            return fallback;
        }
        Double value = Numbers.tryParseDouble(context.get(normalizeId(key)), null);
        return value == null ? fallback : value;
    }

    private boolean contextBoolean(DamageContextVariables context, boolean fallback, String... keys) {
        if (context == null || context.isEmpty() || keys == null || keys.length == 0) {
            return fallback;
        }
        for (String key : keys) {
            if (Texts.isBlank(key) || !context.contains(key)) {
                continue;
            }
            Object raw = context.get(key);
            if (raw instanceof Boolean boolValue) {
                return boolValue;
            }
            String normalized = Texts.toStringSafe(raw).trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) {
                continue;
            }
            if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
        }
        return fallback;
    }

    private String firstNonBlank(String left, String right) {
        return Texts.isBlank(left) ? right : left;
    }

    private record DamageCalculationPlan(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageRequest request,
            double seededRoll) {

    }

    private double resolveRecoveryAmount(DamageContext damageContext, RecoveryDefinition recovery, double finalDamage) {
        if (damageContext == null || recovery == null) {
            return 0D;
        }
        DamageContextVariables.Builder context = damageContext.variables().toBuilder();
        AttributeSnapshot sourceSnapshot = snapshotForRecovery(damageContext, recovery.source());
        AttributeSnapshot resistanceSnapshot = snapshotForRecovery(damageContext, recovery.resistanceSource());
        Map<String, Object> evaluationContext = context.build().asMap();
        double flat = sumAttributes(sourceSnapshot, evaluationContext, recovery.flatAttributes());
        double percent = sumAttributes(sourceSnapshot, evaluationContext, recovery.percentAttributes());
        double resistance = sumAttributes(resistanceSnapshot, evaluationContext, recovery.resistanceAttributes());
        double percentAmount = finalDamage * (percent / 100D);
        double grossRecovery = flat + percentAmount;
        context.put("input", finalDamage);
        context.put("base", finalDamage);
        context.put("damage", finalDamage);
        context.put("final_damage", finalDamage);
        context.put("flat", flat);
        context.put("percent", percent);
        context.put("percent_amount", percentAmount);
        context.put("gross", grossRecovery);
        context.put("resistance", resistance);
        context.put("healing_flat", flat);
        context.put("healing_percent", percent);
        context.put("healing_percent_amount", percentAmount);
        context.put("healing_gross", grossRecovery);
        context.put("healing_resistance", resistance);
        evaluationContext = context.build().asMap();
        double value = Texts.isBlank(recovery.expression())
                ? grossRecovery * (1D - (resistance / 100D))
                : ExpressionEngine.evaluate(recovery.expression(), evaluationContext);
        if (recovery.minResult() != null) {
            value = Math.max(value, recovery.minResult());
        }
        if (recovery.maxResult() != null) {
            value = Math.min(value, recovery.maxResult());
        }
        return Math.max(0D, value);
    }

    private AttributeSnapshot snapshotForRecovery(DamageContext damageContext, DamageStageSource source) {
        if (damageContext == null || source == null) {
            return null;
        }
        return switch (source) {
            case ATTACKER ->
                damageContext.attackerSnapshot();
            case TARGET ->
                damageContext.targetSnapshot();
            case CONTEXT ->
                null;
        };
    }

    private double sumAttributes(AttributeSnapshot snapshot, Map<String, ?> context, java.util.List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        for (String id : ids) {
            if (Texts.isBlank(id)) {
                continue;
            }
            String normalized = normalizeId(id);
            Double value = snapshot == null ? null : snapshot.values().get(normalized);
            if (value == null && context != null) {
                value = Numbers.tryParseDouble(context.get(normalized), null);
            }
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    private String messageOrFallback(String key, String fallback) {
        if (service.plugin() == null || service.plugin().messageService() == null || Texts.isBlank(key)) {
            return fallback;
        }
        String value = service.plugin().messageService().message(key);
        return Texts.isBlank(value) || key.equals(value) ? fallback : value;
    }

    private boolean shouldDebugCombat(DamageContext damageContext) {
        if (damageContext == null) {
            return false;
        }
        return shouldDebugCombat(damageContext.attacker(), damageContext.target(), damageContext.projectile());
    }

    private boolean shouldDebugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile) {
        var debugService = service.combatDebug();
        return projectile != null
                ? debugService.shouldTrace(projectile, target)
                : debugService.shouldTrace(attacker, target);
    }

    private void debugCombat(DamageContext damageContext, String phase, String messageKey) {
        debugCombat(damageContext, phase, messageKey, Map.of());
    }

    private void debugCombat(DamageContext damageContext, String phase, String messageKey, Map<String, ?> replacements) {
        if (damageContext == null) {
            debugCombat((LivingEntity) null, null, null, phase, messageKey, replacements);
            return;
        }
        debugCombat(damageContext.attacker(), damageContext.target(), damageContext.projectile(), phase, messageKey, replacements);
    }

    private void debugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile, String phase, String messageKey) {
        debugCombat(attacker, target, projectile, phase, messageKey, Map.of());
    }

    private void debugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile, String phase, String messageKey, Map<String, ?> replacements) {
        if (!shouldDebugCombat(attacker, target, projectile) || Texts.isBlank(messageKey)) {
            return;
        }
        service.combatDebug().logMessage(phase, messageKey, replacements);
    }

    private String describeDamageContext(DamageContext damageContext) {
        if (damageContext == null) {
            return "<null>";
        }
        return "attacker=" + entityDebugLabel(damageContext.attacker())
                + ", target=" + entityDebugLabel(damageContext.target())
                + ", projectile=" + entityDebugLabel(damageContext.projectile())
                + ", cause=" + (damageContext.cause() == null ? "<none>" : damageContext.cause().name())
                + ", damageType=" + damageContext.damageTypeId()
                + ", sourceDamage=" + formatNumber(damageContext.sourceDamage())
                + ", baseDamage=" + formatNumber(damageContext.baseDamage());
    }

    private String formatStageValues(Map<String, Double> stageValues) {
        if (stageValues == null || stageValues.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> entry : stageValues.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(formatNumber(entry.getValue() == null ? 0D : entry.getValue()));
            first = false;
        }
        builder.append('}');
        return builder.toString();
    }

    private String formatSnapshot(AttributeSnapshot snapshot) {
        if (snapshot == null) {
            return "<null>";
        }
        if (snapshot.values().isEmpty()) {
            return "signature=" + snapshot.sourceSignature() + ", values={}";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("signature=").append(snapshot.sourceSignature()).append(", values={");
        boolean first = true;
        for (Map.Entry<String, Double> entry : orderedSnapshotEntries(snapshot).entrySet()) {
            Double value = entry.getValue();
            if (value == null || Double.compare(value, 0D) == 0) {
                continue;
            }
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(formatNumber(value));
            first = false;
        }
        if (first) {
            builder.append('}');
            return builder.toString();
        }
        builder.append('}');
        return builder.toString();
    }

    private Map<String, Double> orderedSnapshotEntries(AttributeSnapshot snapshot) {
        if (snapshot == null || snapshot.values().isEmpty()) {
            return Map.of();
        }
        Map<String, Double> ordered = new LinkedHashMap<>();
        for (var definition : service.attributeRegistry().all().values()) {
            if (definition == null) {
                continue;
            }
            Double value = snapshot.values().get(definition.id());
            if (value != null) {
                ordered.put(definition.id(), value);
            }
        }
        for (Map.Entry<String, Double> entry : snapshot.values().entrySet()) {
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    private String entityDebugLabel(Entity entity) {
        if (entity == null) {
            return "<none>";
        }
        String name = Texts.toStringSafe(entity.getName()).trim();
        if (Texts.isBlank(name)) {
            name = entity.getType().name();
        }
        return name + "(" + entity.getType().name() + "," + entity.getUniqueId() + ")";
    }

    private String formatNumber(double value) {
        return Numbers.formatNumber(value, "0.##");
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
