package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;
import emaki.jiuwu.craft.attribute.api.event.EmakiAttributeDamageEvent;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.api.model.DamageContext;
import emaki.jiuwu.craft.attribute.api.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.api.model.DamageResult;
import emaki.jiuwu.craft.attribute.formula.AttributeFormulaEvaluator;
import emaki.jiuwu.craft.attribute.model.AttributeContributionTrace;
import emaki.jiuwu.craft.attribute.model.DamageRequest;
import emaki.jiuwu.craft.attribute.model.DamageStageDefinition;
import emaki.jiuwu.craft.attribute.model.DamageStageSource;
import emaki.jiuwu.craft.attribute.model.StageRole;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.attribute.model.ProjectileDamageSnapshot;
import emaki.jiuwu.craft.attribute.model.RecoveryDefinition;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.service.MessageService;

final class DamageCalculationServiceDelegate {

    private final DamageCalculationService ownerRef;

    DamageCalculationServiceDelegate(DamageCalculationService ownerRef) {
        this.ownerRef = ownerRef;
    }

    void refreshCaches() {
        ownerRef.messageDispatcher.refreshCaches();
        ownerRef.cachedDefaultDamageTypeId = ownerRef.computeDefaultDamageTypeId();
        ownerRef.cachedDefaultProjectileDamageTypeId = ownerRef.computeDefaultProjectileDamageTypeId();
    }

    String defaultDamageTypeId() {
        String cached = ownerRef.cachedDefaultDamageTypeId;
        return cached != null ? cached : ownerRef.computeDefaultDamageTypeId();
    }

    String defaultProjectileDamageTypeId() {
        String cached = ownerRef.cachedDefaultProjectileDamageTypeId;
        return cached != null ? cached : ownerRef.computeDefaultProjectileDamageTypeId();
    }

    String computeDefaultDamageTypeId() {
        if (ownerRef.service.config().defaultDamageType() != null && !ownerRef.service.config().defaultDamageType().isBlank()) {
            DamageTypeDefinition definition = ownerRef.service.damageTypeRegistry().resolve(ownerRef.service.config().defaultDamageType());
            if (definition != null) {
                return definition.id();
            }
        }
        if (!ownerRef.service.damageTypeRegistry().all().isEmpty()) {
            return ownerRef.service.damageTypeRegistry().all().values().iterator().next().id();
        }
        return "physical";
    }

    String computeDefaultProjectileDamageTypeId() {
        if (ownerRef.service.config().projectileDamageType() != null && !ownerRef.service.config().projectileDamageType().isBlank()) {
            DamageTypeDefinition configured = ownerRef.service.damageTypeRegistry().resolve(ownerRef.service.config().projectileDamageType());
            if (configured != null) {
                return configured.id();
            }
        }
        DamageTypeDefinition projectile = ownerRef.service.damageTypeRegistry().resolve("projectile");
        if (projectile != null) {
            return projectile.id();
        }
        return ownerRef.computeDefaultDamageTypeId();
    }

    DamageTypeDefinition resolveDamageType(String damageTypeId) {
        String resolvedId = Texts.isBlank(damageTypeId) ? ownerRef.defaultDamageTypeId() : Texts.normalizeId(damageTypeId);
        DamageTypeDefinition definition = ownerRef.service.damageTypeRegistry().resolve(resolvedId);
        if (definition != null) {
            return definition;
        }
        return new DamageTypeDefinition(resolvedId, resolvedId, List.of(), Set.of(), false, List.of(), null);
    }

    void setDamageTypeOverride(LivingEntity entity, String damageTypeId) {
        ownerRef.service.stateRepository().setDamageTypeOverride(entity, damageTypeId);
    }

    String peekDamageTypeOverride(LivingEntity entity) {
        return ownerRef.service.stateRepository().peekDamageTypeOverride(entity);
    }

    String consumeDamageTypeOverride(LivingEntity entity) {
        return ownerRef.service.stateRepository().consumeDamageTypeOverride(entity);
    }

    void markSyntheticDamage(LivingEntity entity, boolean value) {
        ownerRef.service.stateRepository().markSyntheticDamage(entity, value);
    }

    boolean isSyntheticDamage(LivingEntity entity) {
        return ownerRef.service.stateRepository().isSyntheticDamage(entity);
    }

    ProjectileDamageSnapshot snapshotProjectile(Projectile projectile, LivingEntity shooter) {
        if (projectile == null || shooter == null) {
            return null;
        }
        AttributeSnapshot attackSnapshot = ownerRef.service.collectCombatSnapshot(shooter);
        String damageTypeId = ownerRef.consumeDamageTypeOverride(shooter);
        if (Texts.isBlank(damageTypeId)) {
            damageTypeId = ownerRef.defaultProjectileDamageTypeId();
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
                now + ownerRef.service.projectileTtlMs(),
                attackSnapshot
        );
        ownerRef.service.stateRepository().writeProjectileSnapshot(projectile, snapshot);
        if (ownerRef.shouldDebugCombat(shooter, null, projectile)) {
            ownerRef.debugCombat(shooter, null, projectile, "PROJECTILE_SNAPSHOT_WRITE", "combat_debug.projectile_snapshot_write", Map.of(
                    "projectile", ownerRef.service.combatDebug().entityDebugLabel(projectile),
                    "shooter", ownerRef.service.combatDebug().entityDebugLabel(shooter),
                    "damage_type", snapshot.damageTypeId(),
                    "signature", snapshot.sourceSignature(),
                    "snapshot", ownerRef.service.combatDebug().formatSnapshot(snapshot.attackSnapshot())
            ));
        }
        return snapshot;
    }

    ProjectileDamageSnapshot readProjectileSnapshot(Projectile projectile) {
        return ownerRef.service.stateRepository().readProjectileSnapshot(projectile);
    }

    DamageContext createDamageContext(LivingEntity attacker, LivingEntity target, Projectile projectile, EntityDamageEvent.DamageCause cause, String damageTypeId, double sourceDamage, double baseDamage, AttributeSnapshot attackerSnapshot, AttributeSnapshot targetSnapshot, DamageContextVariables context) {
        String resolvedDamageTypeId = ownerRef.resolveRequestedDamageTypeId(attacker, projectile, cause, damageTypeId);
        DamageTypeDefinition damageType = ownerRef.resolveDamageType(resolvedDamageTypeId);
        AttributeSnapshot resolvedAttacker = attackerSnapshot;
        if (resolvedAttacker == null) {
            resolvedAttacker = attacker == null ? AttributeSnapshot.empty("") : ownerRef.service.collectCombatSnapshot(attacker);
        }
        AttributeSnapshot resolvedTarget = targetSnapshot;
        if (resolvedTarget == null) {
            resolvedTarget = target == null ? AttributeSnapshot.empty("") : ownerRef.service.collectCombatSnapshot(target);
        }
        DamageContextVariables.Builder merged = DamageContextVariables.builder();
        merged.putAll(context);
        MessageService messageService = ownerRef.service.plugin() == null ? null : ownerRef.service.plugin().messageService();
        String attackerLabel = ownerRef.messageDispatcher.entityLabel(
                attacker,
                cause,
                messageService == null ? "environment" : messageService.messageOrFallback("damage.environment", "environment")
        );
        String targetLabel = ownerRef.messageDispatcher.entityLabel(
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
        merged.put("cause_name", ownerRef.messageDispatcher.causeDisplayName(cause));
        merged.put("cause_id", cause == null ? "" : Texts.normalizeId(cause.name()));
        merged.put("damage_cause", cause == null ? "" : cause.name());
        merged.put("damage_cause_name", ownerRef.messageDispatcher.causeDisplayName(cause));
        merged.put("damage_cause_id", cause == null ? "" : Texts.normalizeId(cause.name()));
        if (projectile != null) {
            merged.put("projectile_type", projectile.getType().name());
            merged.put("projectile_uuid", projectile.getUniqueId().toString());
        }
        merged.put(ShieldBlockResolver.TARGET_BLOCKING, ShieldBlockResolver.isBlocking(
                target,
                projectile != null ? projectile : attacker,
                ownerRef.service.config().shield()
        ) ? 1D : 0D);
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

    DamageResult calculateDamage(DamageContext damageContext) {
        DamageCalculationService.DamageCalculationPlan plan = ownerRef.prepareDamageCalculation(damageContext);
        if (plan == null) {
            return new DamageResult("", 0D, false, 0D, Map.of(), DamageContext.empty());
        }
        return ownerRef.service.damageEngine().resolve(plan.request(), plan.damageType(), plan.seededRoll());
    }

    DamageResult calculateDamage(LivingEntity attacker, LivingEntity target, String damageTypeId, double baseDamage, DamageContextVariables context) {
        DamageContextVariables effectiveContext = context == null ? DamageContextVariables.empty() : context;
        double sourceDamage = effectiveContext.getDouble("source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = effectiveContext.extractDamageCause();
        DamageContext damageContext = ownerRef.createDamageContext(attacker, target, null, cause, damageTypeId, sourceDamage, baseDamage, null, null, effectiveContext);
        return ownerRef.calculateDamage(damageContext);
    }

    DamageResult calculateDamage(LivingEntity attacker, LivingEntity target, String damageTypeId, double baseDamage, Map<String, ?> context) {
        return ownerRef.calculateDamage(attacker, target, damageTypeId, baseDamage, DamageContextVariables.from(context));
    }

    boolean applyDamage(DamageContext damageContext) {
        if (damageContext.projectile() != null) {
            return ownerRef.applyProjectileDamage(damageContext);
        }
        return ownerRef.applyResolvedDamage(ownerRef.resolveDamageApplication(damageContext), damageContext == null ? null : damageContext.attacker(), 0D);
    }

    boolean applyDamage(LivingEntity attacker, LivingEntity target, String damageTypeId, double baseDamage, DamageContextVariables context) {
        DamageContextVariables effectiveContext = context == null ? DamageContextVariables.empty() : context;
        double sourceDamage = effectiveContext.getDouble("source_damage", baseDamage);
        EntityDamageEvent.DamageCause cause = effectiveContext.extractDamageCause();
        DamageContext damageContext = ownerRef.createDamageContext(attacker, target, null, cause, damageTypeId, sourceDamage, baseDamage, null, null, effectiveContext);
        return ownerRef.applyDamage(damageContext);
    }

    boolean applyDamage(LivingEntity attacker, LivingEntity target, String damageTypeId, double baseDamage, Map<String, ?> context) {
        return ownerRef.applyDamage(attacker, target, damageTypeId, baseDamage, DamageContextVariables.from(context));
    }

    void clearPlayerDamageTypeOverride(Player player) {
        ownerRef.service.stateRepository().setDamageTypeOverride(player, null);
    }

    DamageResult resolveCalculation(DamageCalculationService.DamageCalculationPlan plan) {
        if (plan == null) {
            return null;
        }
        return ownerRef.service.damageEngine().resolve(plan.request(), plan.damageType(), plan.seededRoll());
    }

    CompletableFuture<DamageResult> resolveCalculationAsync(DamageCalculationService.DamageCalculationPlan plan) {
        return ownerRef.service.asyncDamageEngine().resolveAsync(plan.request(), plan.damageType(), plan.seededRoll());
    }

    DamageContext detachForWorker(DamageContext context) {
        if (context == null) {
            return DamageContext.empty();
        }
        return DamageContext.of(
                null,
                null,
                null,
                context.cause(),
                context.damageTypeId(),
                context.sourceDamage(),
                context.baseDamage(),
                context.attackerSnapshot(),
                context.targetSnapshot(),
                context.variables()
        );
    }

    CompletableFuture<Boolean> applyResolvedDamageAsync(ResolvedDamage resolvedDamage, double alreadyAppliedDamage) {
        return ownerRef.applyResolvedDamageAsync(resolvedDamage, null, alreadyAppliedDamage);
    }

    CompletableFuture<Boolean> applyAttackerSideEffects(DamageCalculationService.TargetApplicationResult targetResult) {
        if (targetResult == null || !targetResult.applied()) {
            return CompletableFuture.completedFuture(false);
        }
        DamageCalculationService.AttackerSideEffectsIntent intent = targetResult.attackerIntent();
        if (intent == null || intent.attackerId() == null) {
            return CompletableFuture.completedFuture(true);
        }
        return ownerRef.callEntityOwner(intent.attackerId(), attacker -> {
            if (!attacker.isValid()) {
                return false;
            }
            if (intent.recoveryAmount() > 0D) {
                ownerRef.runPostDamageEffect(
                        "attacker-recovery",
                        () -> ownerRef.recoveryCalculator.applyRecovery(attacker, intent.recoveryAmount())
                );
            }
            if (attacker instanceof Player player) {
                ownerRef.runPostDamageEffect(
                        "attacker-cooldown",
                        () -> ownerRef.service.startAttackCooldown(
                                player,
                                intent.attackerSnapshot(),
                                player.getInventory().getItemInMainHand()
                        )
                );
                ownerRef.runPostDamageEffect("attacker-message", () -> ownerRef.messageDispatcher.dispatch(player, intent.message()));
            }
            return true;
        }).handle((ignored, throwable) -> true);
    }

    void runPostDamageEffect(String phase, Runnable operation) {
        ownerRef.completePostDamageEffect(phase, () -> {
            operation.run();
            return null;
        }, null);
    }

    <T> T completePostDamageEffect(String phase, Supplier<T> operation, T fallback) {
        if (operation == null) {
            return fallback;
        }
        try {
            return operation.get();
        } catch (RuntimeException | LinkageError exception) {
            if (ownerRef.service.plugin() != null) {
                ownerRef.service.plugin().getLogger().log(
                        Level.WARNING,
                        "[combat] 伤害后置效果 '" + phase + "' 执行失败；其余终结效果将继续处理",
                        exception
                );
            }
            return fallback;
        }
    }

    void applyDamageSideEffects(ResolvedDamage resolvedDamage, Entity visualSource) {
        if (resolvedDamage == null || resolvedDamage.damageContext() == null) {
            return;
        }
        DamageContext damageContext = resolvedDamage.damageContext();
        LivingEntity target = damageContext.target();
        int cooldownTicks = 0;
        if (damageContext.attacker() instanceof Player player) {
            cooldownTicks = ownerRef.service.startAttackCooldown(player, damageContext.attackerSnapshot(), player.getInventory().getItemInMainHand());
        }
        ownerRef.applyRecovery(damageContext, resolvedDamage.damageType(), resolvedDamage.damageResult(), resolvedDamage.finalDamage());
        ownerRef.notifyDamageMessages(damageContext, resolvedDamage.damageType(), resolvedDamage.damageResult(), resolvedDamage.finalDamage());
        if (target != null) {
            ownerRef.service.scheduleHealthSync(target);
        }
        if (ownerRef.shouldDebugCombat(damageContext)) {
            ownerRef.debugCombat(damageContext, "PERFECT_SIDE_EFFECTS", "combat_debug.perfect_side_effects", Map.of(
                    "final_damage", ownerRef.service.combatDebug().formatNumber(resolvedDamage.finalDamage()),
                    "attacker_cooldown_ticks", cooldownTicks
            ));
            ownerRef.service.damageTraceService().record(resolvedDamage, "perfect_side_effects", false, true, true, List.of("PERFECT_SIDE_EFFECTS"));
        }
    }

    String aggroSkipReason(LivingEntity target, LivingEntity attacker) {
        if (!(target instanceof Mob)) {
            return "not_mob";
        }
        if (attacker == null) {
            return "attacker_absent";
        }
        if (!attacker.isValid()) {
            return "attacker_invalid";
        }
        if (attacker.isDead()) {
            return "attacker_dead";
        }
        if (!target.isValid()) {
            return "target_invalid";
        }
        if (target.isDead()) {
            return "target_dead";
        }
        if (target.getUniqueId().equals(attacker.getUniqueId())) {
            return "self_target";
        }
        return null;
    }

    void applyDirectDamage(LivingEntity target, double damage, Entity visualSource, DamageContext damageContext) {
        if (target == null || damage <= 0D || !target.isValid() || target.isDead()) {
            return;
        }
        DamageSource damageSource = ownerRef.syntheticDamageDispatcher.buildDamageSource(damageContext, visualSource);
        target.setNoDamageTicks(0);
        ownerRef.markSyntheticDamage(target, true);
        try {
            target.damage(damage, damageSource);
        } finally {
            ownerRef.markSyntheticDamage(target, false);
        }
    }

    String resolveRequestedDamageTypeId(LivingEntity attacker, Projectile projectile, EntityDamageEvent.DamageCause cause, String damageTypeId) {
        if (Texts.isNotBlank(damageTypeId)) {
            return Texts.normalizeId(damageTypeId);
        }
        if (attacker != null) {
            String override = ownerRef.consumeDamageTypeOverride(attacker);
            if (Texts.isNotBlank(override)) {
                return Texts.normalizeId(override);
            }
        }
        if (projectile != null || cause == EntityDamageEvent.DamageCause.PROJECTILE) {
            return ownerRef.defaultProjectileDamageTypeId();
        }
        return ownerRef.defaultDamageTypeId();
    }

    boolean isResolvableDamageContext(DamageContext damageContext) {
        return damageContext != null
                && damageContext.target() != null
                && damageContext.target().isValid()
                && !damageContext.target().isDead();
    }

    DamageTypeDefinition filterDamageType(DamageTypeDefinition damageType, boolean allowCritical, boolean calculateTargetDefense) {
        if (damageType == null || (allowCritical && calculateTargetDefense)) {
            return damageType;
        }
        List<DamageStageDefinition> filteredStages = new ArrayList<>();
        for (var stage : damageType.stages()) {
            if (stage == null) {
                continue;
            }
            if (!allowCritical && stage.role() == StageRole.CRITICAL) {
                continue;
            }
            if (!calculateTargetDefense && stage.role() == StageRole.DEFENSE) {
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

    DamageTypeDefinition emptyDamageType(DamageTypeDefinition damageType) {
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

    double readSnapshotAttribute(AttributeSnapshot snapshot, String attributeId) {
        if (snapshot == null || snapshot.values().isEmpty() || Texts.isBlank(attributeId)) {
            return 0D;
        }
        Double value = snapshot.values().get(Texts.normalizeId(attributeId));
        return value == null ? 0D : value;
    }

    void applyRecovery(DamageContext damageContext, DamageTypeDefinition damageType, DamageResult result, double finalDamage) {
        ownerRef.recoveryCalculator.applyRecovery(damageContext, damageType, result, finalDamage);
    }

    void notifyDamageMessages(DamageContext damageContext, DamageTypeDefinition damageType, DamageResult result, double finalDamage) {
        ownerRef.messageDispatcher.notifyDamageMessages(damageContext, damageType, result, finalDamage);
    }

    <T> CompletableFuture<T> callEntityOwner(UUID entityId, Function<LivingEntity, T> operation) {
        if (entityId == null || operation == null || ownerRef.service.plugin() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("实体所有者分派不可用。"));
        }
        EmakiScheduling sched = ownerRef.scheduling();
        if (sched == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("实体所有者分派器不可用。"));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            sched.runGlobal(ownerRef.service.plugin(), () -> {
                Entity entity = Bukkit.getEntity(entityId);
                if (!(entity instanceof LivingEntity livingEntity) || !livingEntity.isValid()) {
                    result.completeExceptionally(new IllegalStateException("实体已不可用: " + entityId));
                    return;
                }
                try {
                    sched.runForEntity(
                            ownerRef.service.plugin(),
                            livingEntity,
                            () -> {
                                try {
                                    result.complete(operation.apply(livingEntity));
                                } catch (Throwable throwable) {
                                    result.completeExceptionally(throwable);
                                }
                            },
                            () -> result.completeExceptionally(new IllegalStateException(
                                    "实体在所有者操作前已退役: " + entityId))
                    );
                } catch (Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
            });
        } catch (Throwable throwable) {
            result.completeExceptionally(throwable);
        }
        return result;
    }

    EmakiScheduling scheduling() {
        EmakiScheduling sched = ownerRef.service.scheduling();
        return sched != null ? sched : ownerRef.service.plugin().scheduling();
    }

    UUID parseUuid(String value) {
        if (Texts.isBlank(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    String firstNonBlank(String left, String right) {
        return Texts.isBlank(left) ? right : left;
    }

    double resolveRecoveryAmount(DamageContext damageContext, RecoveryDefinition recovery, double finalDamage) {
        if (damageContext == null || recovery == null) {
            return 0D;
        }
        DamageContextVariables.Builder context = damageContext.variables().toBuilder();
        AttributeSnapshot sourceSnapshot = ownerRef.snapshotForRecovery(damageContext, recovery.source());
        AttributeSnapshot resistanceSnapshot = ownerRef.snapshotForRecovery(damageContext, recovery.resistanceSource());
        Map<String, Object> evaluationContext = context.build().asMap();
        double flat = ownerRef.sumAttributes(sourceSnapshot, evaluationContext, recovery.flatAttributes());
        double percent = ownerRef.sumAttributes(sourceSnapshot, evaluationContext, recovery.percentAttributes());
        double resistance = ownerRef.sumAttributes(resistanceSnapshot, evaluationContext, recovery.resistanceAttributes());
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
        double defaultValue = grossRecovery * (1D - (resistance / 100D));
        return AttributeFormulaEvaluator.evaluate(recovery.expression(), evaluationContext, defaultValue, recovery.minResult(), recovery.maxResult());
    }

    AttributeSnapshot snapshotForRecovery(DamageContext damageContext, DamageStageSource source) {
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

    double sumAttributes(AttributeSnapshot snapshot, Map<String, ?> context, List<String> ids) {
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

    void debugDamagePlan(DamageCalculationService.DamageCalculationPlan plan) {
        if (plan == null) {
            return;
        }
        DamageContext context = plan.damageContext();
        if (!ownerRef.shouldDebugDamage(context)) {
            return;
        }
        try {
            ownerRef.emitDamagePlanAnchors(plan, context);
        } catch (RuntimeException | LinkageError exception) {
            if (ownerRef.service.plugin() != null) {
                ownerRef.service.plugin().getLogger().log(
                        Level.WARNING,
                        "[combat] 伤害调试锚点输出失败；伤害计算不受影响",
                        exception
                );
            }
        }
    }

    String debugStageLangKey(DamageStageDefinition stage) {
        return switch (stage.role()) {
            case CRITICAL -> "combat.damage_critical";
            case DEFENSE -> "combat.damage_reduction";
            case BLOCK -> "combat.damage_block";
            case NORMAL -> "combat.damage_bonus";
        };
    }

}
