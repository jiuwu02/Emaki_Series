package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

final class DamageCalculationService {

    private final AttributeService service;
    private final DamageRecoveryCalculator recoveryCalculator;
    private final DamageMessageDispatcher messageDispatcher;
    private final SyntheticDamageDispatcher syntheticDamageDispatcher;

    DamageCalculationService(AttributeService service) {
        this.service = service;
        this.recoveryCalculator = new DamageRecoveryCalculator(service);
        this.messageDispatcher = new DamageMessageDispatcher(service);
        this.syntheticDamageDispatcher = new SyntheticDamageDispatcher(service);
    }

    void refreshCaches() {
        messageDispatcher.refreshCaches();
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
        String resolvedId = Texts.isBlank(damageTypeId) ? defaultDamageTypeId() : Texts.normalizeId(damageTypeId);
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
                Texts.normalizeId(damageTypeId),
                shooter.getUniqueId(),
                sourceSignature,
                now,
                now + service.projectileTtlMs(),
                attackSnapshot
        );
        service.stateRepository().writeProjectileSnapshot(projectile, snapshot);
        if (shouldDebugCombat(shooter, null, projectile)) {
            debugCombat(shooter, null, projectile, "PROJECTILE_SNAPSHOT_WRITE", "combat_debug.projectile_snapshot_write", Map.of(
                    "projectile", service.combatDebug().entityDebugLabel(projectile),
                    "shooter", service.combatDebug().entityDebugLabel(shooter),
                    "damage_type", snapshot.damageTypeId(),
                    "signature", snapshot.sourceSignature(),
                    "snapshot", service.combatDebug().formatSnapshot(snapshot.attackSnapshot())
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
        MessageService messageService = service.plugin() == null ? null : service.plugin().messageService();
        String attackerLabel = messageDispatcher.entityLabel(
                attacker,
                cause,
                messageService == null ? "environment" : messageService.messageOrFallback("damage.environment", "environment")
        );
        String targetLabel = messageDispatcher.entityLabel(
                target,
                null,
                messageService == null ? "target" : messageService.messageOrFallback("damage.target", "target")
        );
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
        merged.put("cause_name", messageDispatcher.causeDisplayName(cause));
        merged.put("cause_id", cause == null ? "" : Texts.normalizeId(cause.name()));
        merged.put("damage_cause", cause == null ? "" : cause.name());
        merged.put("damage_cause_name", messageDispatcher.causeDisplayName(cause));
        merged.put("damage_cause_id", cause == null ? "" : Texts.normalizeId(cause.name()));
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
            return new DamageResult("", 0D, false, 0D, Map.of(), DamageContext.empty());
        }
        return service.damageEngine().resolve(plan.request(), plan.damageType(), plan.seededRoll());
    }

    public DamageResult calculateDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            DamageContextVariables context) {
        DamageContextVariables effectiveContext = context == null ? DamageContextVariables.empty() : context;
        double sourceDamage = effectiveContext.getDouble("source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = effectiveContext.extractDamageCause();
        DamageContext damageContext = createDamageContext(attacker, target, null, cause, damageTypeId, sourceDamage, baseDamage, effectiveContext);
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
        DamageContextVariables effectiveContext = context == null ? DamageContextVariables.empty() : context;
        double sourceDamage = effectiveContext.getDouble("source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = effectiveContext.extractDamageCause();
        DamageContext damageContext = createDamageContext(attacker, target, null, cause, damageTypeId, sourceDamage, baseDamage, effectiveContext);
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
                        "projectile", service.combatDebug().entityDebugLabel(projectile)
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
        DamageContextVariables effectiveContext = context == null ? DamageContextVariables.empty() : context;
        double sourceDamage = effectiveContext.getDouble("source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = effectiveContext.extractDamageCause();
        DamageContext damageContext = createDamageContext(shooter, target, projectile, cause, defaultProjectileDamageTypeId(), sourceDamage, baseDamage, effectiveContext);
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
                    "context", service.combatDebug().describeDamageContext(damageContext),
                    "attacker_snapshot", service.combatDebug().formatSnapshot(damageContext.attackerSnapshot()),
                    "target_snapshot", service.combatDebug().formatSnapshot(damageContext.targetSnapshot())
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
                    "context", service.combatDebug().describeDamageContext(damageContext),
                    "attacker_snapshot", service.combatDebug().formatSnapshot(damageContext.attackerSnapshot()),
                    "target_snapshot", service.combatDebug().formatSnapshot(damageContext.targetSnapshot())
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
        ResolvedDamage effectiveResolvedDamage = syntheticDamageDispatcher.dispatchIfNeeded(resolvedDamage, visualSource);
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
                    "final_damage", service.combatDebug().formatNumber(effectiveResolvedDamage.finalDamage()),
                    "already_applied", service.combatDebug().formatNumber(appliedDamage),
                    "remaining", service.combatDebug().formatNumber(remainingDamage),
                    "target_health_before", service.combatDebug().formatNumber(healthBefore),
                    "target_absorption_before", service.combatDebug().formatNumber(absorptionBefore),
                    "visual_source", service.combatDebug().entityDebugLabel(visualSource)
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
                    "target_health_after", service.combatDebug().formatNumber(target.getHealth()),
                    "target_absorption_after", service.combatDebug().formatNumber(target.getAbsorptionAmount()),
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
            return Texts.normalizeId(damageTypeId);
        }
        if (attacker != null) {
            String override = consumeDamageTypeOverride(attacker);
            if (Texts.isNotBlank(override)) {
                return Texts.normalizeId(override);
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
        boolean allowCritical = resolvedContext.variables().getBoolean(true, "allow_critical", "critical");
        boolean allowTargetDodge = resolvedContext.variables().getBoolean(false, "allow_target_dodge", "target_dodge", "allow_dodge", "dodge");
        boolean calculateTargetDefense = resolvedContext.variables().getBoolean(
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
                            "dodge_chance", service.combatDebug().formatNumber(dodgeChance),
                            "dodge_roll", service.combatDebug().formatNumber(dodgeRoll)
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
                    "final_damage", service.combatDebug().formatNumber(result.finalDamage()),
                    "critical", result.critical(),
                    "roll", service.combatDebug().formatNumber(result.roll()),
                    "stages", service.combatDebug().formatStageValues(result.stageValues())
            ));
        }
        EmakiAttributeDamageEvent event = new EmakiAttributeDamageEvent(damageContext, result);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getFinalDamage() <= 0D) {
            if (shouldDebugCombat(damageContext)) {
                debugCombat(damageContext, "EVENT_BLOCKED", "combat_debug.event_blocked", Map.of(
                        "cancelled", event.isCancelled(),
                        "final_damage", service.combatDebug().formatNumber(event.getFinalDamage())
                ));
            }
            return null;
        }
        DamageTypeDefinition damageType = resolveDamageType(result.damageTypeId());
        if (shouldDebugCombat(damageContext)) {
            debugCombat(damageContext, "EVENT_PASSED", "combat_debug.event_passed", Map.of(
                    "final_damage", service.combatDebug().formatNumber(event.getFinalDamage()),
                    "resolved_damage_type", damageType.id()
            ));
        }
        return new ResolvedDamage(damageContext, result, damageType, event.getFinalDamage());
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
        String normalized = Texts.normalizeId(stageId);
        return "crit".equals(normalized) || "critical".equals(normalized);
    }

    private boolean isDefenseStage(String stageId) {
        String normalized = Texts.normalizeId(stageId);
        return "defense".equals(normalized) || "target_defense".equals(normalized);
    }

    private double readSnapshotAttribute(AttributeSnapshot snapshot, String attributeId) {
        if (snapshot == null || snapshot.values().isEmpty() || Texts.isBlank(attributeId)) {
            return 0D;
        }
        Double value = snapshot.values().get(Texts.normalizeId(attributeId));
        return value == null ? 0D : value;
    }

    private void applyRecovery(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageResult result,
            double finalDamage) {
        recoveryCalculator.applyRecovery(damageContext, damageType, result, finalDamage);
    }

    private void notifyDamageMessages(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageResult result,
            double finalDamage) {
        messageDispatcher.notifyDamageMessages(damageContext, damageType, result, finalDamage);
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
            String normalized = Texts.normalizeId(id);
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

}
