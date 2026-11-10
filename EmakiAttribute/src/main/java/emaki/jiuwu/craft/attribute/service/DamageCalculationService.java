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

final class DamageCalculationService {

    static final String DEBUG_MODULE_COMBAT = "combat";
    static final String DEBUG_NONE = "-";

    final AttributeService service;
    final DamageRecoveryCalculator recoveryCalculator;
    final DamageMessageDispatcher messageDispatcher;
    final SyntheticDamageDispatcher syntheticDamageDispatcher;

    volatile String cachedDefaultDamageTypeId;
    volatile String cachedDefaultProjectileDamageTypeId;

    DamageCalculationService(AttributeService service) {
        this.service = service;
        this.recoveryCalculator = new DamageRecoveryCalculator(service);
        this.messageDispatcher = new DamageMessageDispatcher(service);
        this.syntheticDamageDispatcher = new SyntheticDamageDispatcher(service);
    }

    void refreshCaches() { new DamageCalculationServiceDelegate(this).refreshCaches(); }

    public String defaultDamageTypeId() { return new DamageCalculationServiceDelegate(this).defaultDamageTypeId(); }

    public String defaultProjectileDamageTypeId() { return new DamageCalculationServiceDelegate(this).defaultProjectileDamageTypeId(); }

    String computeDefaultDamageTypeId() { return new DamageCalculationServiceDelegate(this).computeDefaultDamageTypeId(); }

    String computeDefaultProjectileDamageTypeId() { return new DamageCalculationServiceDelegate(this).computeDefaultProjectileDamageTypeId(); }

    public DamageTypeDefinition resolveDamageType(String damageTypeId) { return new DamageCalculationServiceDelegate(this).resolveDamageType(damageTypeId); }

    public void setDamageTypeOverride(LivingEntity entity, String damageTypeId) { new DamageCalculationServiceDelegate(this).setDamageTypeOverride(entity, damageTypeId); }

    public String peekDamageTypeOverride(LivingEntity entity) { return new DamageCalculationServiceDelegate(this).peekDamageTypeOverride(entity); }

    public String consumeDamageTypeOverride(LivingEntity entity) { return new DamageCalculationServiceDelegate(this).consumeDamageTypeOverride(entity); }

    public void markSyntheticDamage(LivingEntity entity, boolean value) { new DamageCalculationServiceDelegate(this).markSyntheticDamage(entity, value); }

    public boolean isSyntheticDamage(LivingEntity entity) { return new DamageCalculationServiceDelegate(this).isSyntheticDamage(entity); }

    public ProjectileDamageSnapshot snapshotProjectile(Projectile projectile, LivingEntity shooter) { return new DamageCalculationServiceDelegate(this).snapshotProjectile(projectile, shooter); }

    public ProjectileDamageSnapshot readProjectileSnapshot(Projectile projectile) { return new DamageCalculationServiceDelegate(this).readProjectileSnapshot(projectile); }

    public DamageContext createDamageContext(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId,
            double sourceDamage,
            double baseDamage,
            AttributeSnapshot attackerSnapshot,
            AttributeSnapshot targetSnapshot,
            DamageContextVariables context) { return new DamageCalculationServiceDelegate(this).createDamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, attackerSnapshot, targetSnapshot, context); }

    public DamageResult calculateDamage(DamageContext damageContext) { return new DamageCalculationServiceDelegate(this).calculateDamage(damageContext); }

    public DamageResult calculateDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            DamageContextVariables context) { return new DamageCalculationServiceDelegate(this).calculateDamage(attacker, target, damageTypeId, baseDamage, context); }

    public DamageResult calculateDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, ?> context) { return new DamageCalculationServiceDelegate(this).calculateDamage(attacker, target, damageTypeId, baseDamage, context); }

    public boolean applyDamage(DamageContext damageContext) { return new DamageCalculationServiceDelegate(this).applyDamage(damageContext); }

    public boolean applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            DamageContextVariables context) { return new DamageCalculationServiceDelegate(this).applyDamage(attacker, target, damageTypeId, baseDamage, context); }

    public boolean applyDamage(LivingEntity attacker,
            LivingEntity target,
            String damageTypeId,
            double baseDamage,
            Map<String, ?> context) { return new DamageCalculationServiceDelegate(this).applyDamage(attacker, target, damageTypeId, baseDamage, context); }

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
        DamageContext damageContext = createDamageContext(shooter, target, projectile, cause, defaultProjectileDamageTypeId(), sourceDamage, baseDamage, null, null, effectiveContext);
        return applyProjectileDamage(damageContext);
    }

    public boolean applyProjectileDamage(Projectile projectile,
            LivingEntity target,
            double baseDamage,
            Map<String, ?> context) {
        return applyProjectileDamage(projectile, target, baseDamage, DamageContextVariables.from(context));
    }

    public void clearPlayerDamageTypeOverride(Player player) { new DamageCalculationServiceDelegate(this).clearPlayerDamageTypeOverride(player); }

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
        DamageResult result = resolveCalculation(plan);
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
        UUID targetId = damageContext.target().getUniqueId();
        return resolveCalculationAsync(plan)
                .thenCompose(result -> callEntityOwner(
                        targetId,
                        target -> finalizeResolvedDamage(plan.damageContext(), result)
                ));
    }

    DamageResult resolveCalculation(DamageCalculationPlan plan) { return new DamageCalculationServiceDelegate(this).resolveCalculation(plan); }

    CompletableFuture<DamageResult> resolveCalculationAsync(DamageCalculationPlan plan) { return new DamageCalculationServiceDelegate(this).resolveCalculationAsync(plan); }

    private DamageContext detachForWorker(DamageContext context) { return new DamageCalculationServiceDelegate(this).detachForWorker(context); }

    public boolean applyResolvedDamage(ResolvedDamage resolvedDamage, Entity visualSource, double alreadyAppliedDamage) {
        if (resolvedDamage == null || resolvedDamage.damageContext() == null) {
            return false;
        }
        ResolvedDamage effectiveResolvedDamage = syntheticDamageDispatcher.dispatchIfNeeded(resolvedDamage, visualSource);
        if (effectiveResolvedDamage == null || effectiveResolvedDamage.damageContext() == null) {
            if (shouldDebugCombat(resolvedDamage.damageContext())) {
                debugCombat(resolvedDamage.damageContext(), "AGGRO_ABORTED", "combat_debug.aggro_aborted", Map.of(
                        "target", service.combatDebug().entityDebugLabel(resolvedDamage.damageContext().target()),
                        "attacker", service.combatDebug().entityDebugLabel(resolvedDamage.damageContext().attacker()),
                        "target_is_mob", resolvedDamage.damageContext().target() instanceof Mob
                ));
            }
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
        applyDirectDamage(target, remainingDamage, visualSource, damageContext);
        applyAggroTarget(target, damageContext.attacker());
        int cooldownTicks = 0;
        if (damageContext.attacker() instanceof Player player) {
            cooldownTicks = service.startAttackCooldown(player, damageContext.attackerSnapshot(), player.getInventory().getItemInMainHand());
        }
        applyRecovery(damageContext, effectiveResolvedDamage.damageType(), effectiveResolvedDamage.damageResult(), effectiveResolvedDamage.finalDamage());
        notifyDamageMessages(damageContext, effectiveResolvedDamage.damageType(), effectiveResolvedDamage.damageResult(), effectiveResolvedDamage.finalDamage());
        service.scheduleHealthSync(target);
        boolean applied = remainingDamage > 0D || appliedDamage > 0D;
        if (shouldDebugCombat(damageContext)) {
            debugCombat(damageContext, "APPLY_DONE", "combat_debug.apply_done", Map.of(
                    "target_health_after", service.combatDebug().formatNumber(target.getHealth()),
                    "target_absorption_after", service.combatDebug().formatNumber(target.getAbsorptionAmount()),
                    "attacker_cooldown_ticks", cooldownTicks
            ));
            service.damageTraceService().record(effectiveResolvedDamage, "synthetic_apply", false, false, applied, List.of("APPLY_DONE"));
        }
        return applied;
    }

    public CompletableFuture<Boolean> applyResolvedDamageAsync(ResolvedDamage resolvedDamage,
            double alreadyAppliedDamage) { return new DamageCalculationServiceDelegate(this).applyResolvedDamageAsync(resolvedDamage, alreadyAppliedDamage); }

    public CompletableFuture<Boolean> applyResolvedDamageAsync(ResolvedDamage resolvedDamage,
            Entity visualSource,
            double alreadyAppliedDamage) {
        if (resolvedDamage == null || resolvedDamage.damageContext() == null
                || resolvedDamage.damageContext().target() == null) {
            return CompletableFuture.completedFuture(false);
        }
        UUID targetId = resolvedDamage.damageContext().target().getUniqueId();
        return callEntityOwner(targetId, target -> applyTargetSideEffects(
                resolvedDamage,
                target,
                alreadyAppliedDamage,
                true,
                visualSource
        )).thenCompose(this::applyAttackerSideEffects);
    }

    public CompletableFuture<Boolean> applyDamageSideEffectsAsync(ResolvedDamage resolvedDamage) {
        if (resolvedDamage == null || resolvedDamage.damageContext() == null
                || resolvedDamage.damageContext().target() == null) {
            return CompletableFuture.completedFuture(false);
        }
        UUID targetId = resolvedDamage.damageContext().target().getUniqueId();
        return callEntityOwner(targetId, target -> applyTargetSideEffects(
                resolvedDamage,
                target,
                resolvedDamage.finalDamage(),
                false,
                null
        )).thenCompose(this::applyAttackerSideEffects);
    }

    TargetApplicationResult applyTargetSideEffects(ResolvedDamage resolvedDamage,
            LivingEntity target,
            double alreadyAppliedDamage,
            boolean applyDamage,
            Entity visualSource) {
        if (target == null || !target.isValid() || target.isDead()) {
            return TargetApplicationResult.failed();
        }
        DamageContext damageContext = resolvedDamage.damageContext();
        String expectedTargetId = damageContext.variables().string("target_uuid", "");
        if (Texts.isNotBlank(expectedTargetId) && !target.getUniqueId().toString().equals(expectedTargetId)) {
            return TargetApplicationResult.failed();
        }

        double appliedDamage = Math.max(0D, alreadyAppliedDamage);
        double remainingDamage = applyDamage
                ? Math.max(0D, resolvedDamage.finalDamage() - appliedDamage)
                : 0D;
        if (applyDamage) {
            applyDirectDamage(target, remainingDamage, visualSource, damageContext);
        }
        applyAggroTarget(target, damageContext.attacker());

        DamageMessageDispatcher.MessagePlan messages = messageDispatcher.prepareMessages(
                damageContext,
                resolvedDamage.damageType(),
                resolvedDamage.damageResult(),
                resolvedDamage.finalDamage()
        );
        Player targetPlayer = target instanceof Player player ? player : null;
        String attackerId = damageContext.variables().string("attacker_uuid", "");
        boolean sameOwner = Texts.isNotBlank(attackerId) && attackerId.equals(target.getUniqueId().toString());
        double recoveryAmount = recoveryCalculator.calculateRecoveryAmount(
                damageContext,
                resolvedDamage.damageType(),
                resolvedDamage.damageResult(),
                resolvedDamage.finalDamage()
        );
        int cooldownTicks = 0;
        if (sameOwner) {
            double sameOwnerRecovery = recoveryAmount;
            runPostDamageEffect("same-owner-recovery", () -> recoveryCalculator.applyRecovery(target, sameOwnerRecovery));
            recoveryAmount = 0D;
            if (targetPlayer != null) {
                cooldownTicks = completePostDamageEffect(
                        "same-owner-cooldown",
                        () -> service.startAttackCooldown(
                                targetPlayer,
                                damageContext.attackerSnapshot(),
                                targetPlayer.getInventory().getItemInMainHand()
                        ),
                        0
                );
                runPostDamageEffect("same-owner-message", () -> messageDispatcher.dispatch(targetPlayer, messages.attacker()));
            }
        } else {
            runPostDamageEffect("target-message", () -> messageDispatcher.dispatch(targetPlayer, messages.target()));
        }
        runPostDamageEffect("target-health-sync", () -> service.scheduleHealthSync(target));

        boolean applied = !applyDamage || remainingDamage > 0D || appliedDamage > 0D;
        AttackerSideEffectsIntent attackerIntent = sameOwner
                ? null
                : new AttackerSideEffectsIntent(
                        parseUuid(attackerId),
                        damageContext.attackerSnapshot(),
                        recoveryAmount,
                        messages.attacker(),
                        "player".equalsIgnoreCase(damageContext.variables().string("attacker_type", ""))
                );
        return new TargetApplicationResult(applied, cooldownTicks, attackerIntent);
    }

    CompletableFuture<Boolean> applyAttackerSideEffects(TargetApplicationResult targetResult) { return new DamageCalculationServiceDelegate(this).applyAttackerSideEffects(targetResult); }

    void runPostDamageEffect(String phase, Runnable operation) { new DamageCalculationServiceDelegate(this).runPostDamageEffect(phase, operation); }

    <T> T completePostDamageEffect(String phase, Supplier<T> operation, T fallback) { return new DamageCalculationServiceDelegate(this).completePostDamageEffect(phase, operation, fallback); }

    public void applyDamageSideEffects(ResolvedDamage resolvedDamage, Entity visualSource) { new DamageCalculationServiceDelegate(this).applyDamageSideEffects(resolvedDamage, visualSource); }

    private void applyAggroTarget(LivingEntity target, LivingEntity attacker) {
        String skipReason = aggroSkipReason(target, attacker);
        if (skipReason != null) {
            if (shouldDebugCombat(attacker, target, null)) {
                debugCombat(attacker, target, null, "AGGRO_SKIPPED", "combat_debug.aggro_skipped", Map.of(
                        "reason", skipReason,
                        "target", service.combatDebug().entityDebugLabel(target),
                        "attacker", service.combatDebug().entityDebugLabel(attacker),
                        "target_is_mob", target instanceof Mob
                ));
            }
            return;
        }
        Mob mob = (Mob) target;
        boolean tracing = shouldDebugCombat(attacker, target, null);
        boolean awareBefore = tracing && mob.isAware();
        LivingEntity targetBefore = tracing ? mob.getTarget() : null;
        mob.setAware(true);
        mob.setTarget(attacker);
        if (!tracing) {
            return;
        }
        debugCombat(attacker, target, null, "AGGRO_APPLIED", "combat_debug.aggro_applied", Map.of(
                "target", service.combatDebug().entityDebugLabel(target),
                "attacker", service.combatDebug().entityDebugLabel(attacker),
                "aware_before", awareBefore,
                "aware_after", mob.isAware(),
                "target_before", service.combatDebug().entityDebugLabel(targetBefore),
                "target_after", service.combatDebug().entityDebugLabel(mob.getTarget())
        ));
    }

    private String aggroSkipReason(LivingEntity target, LivingEntity attacker) { return new DamageCalculationServiceDelegate(this).aggroSkipReason(target, attacker); }

    private void applyDirectDamage(LivingEntity target, double damage, Entity visualSource, DamageContext damageContext) { new DamageCalculationServiceDelegate(this).applyDirectDamage(target, damage, visualSource, damageContext); }

    String resolveRequestedDamageTypeId(LivingEntity attacker,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId) { return new DamageCalculationServiceDelegate(this).resolveRequestedDamageTypeId(attacker, projectile, cause, damageTypeId); }

    private boolean isResolvableDamageContext(DamageContext damageContext) { return new DamageCalculationServiceDelegate(this).isResolvableDamageContext(damageContext); }

    DamageCalculationPlan prepareDamageCalculation(DamageContext damageContext) {
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
        double flatRoll = ThreadLocalRandom.current().nextDouble(0D, 1D);
        variables.put("allow_critical", allowCritical);
        variables.put("allow_target_dodge", allowTargetDodge);
        variables.put("calculate_target_defense", calculateTargetDefense);
        variables.put("damage_roll", flatRoll);
        variables.put("damage_roll_flat", flatRoll);
        variables.put("damage_roll_percent", ThreadLocalRandom.current().nextDouble(0D, 1D));
        variables.put("damage_roll_chance", ThreadLocalRandom.current().nextDouble(0D, 1D));
        variables.put("damage_roll_multiplier", ThreadLocalRandom.current().nextDouble(0D, 1D));
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
                DamageCalculationPlan dodgedPlan = new DamageCalculationPlan(
                        resolvedContext.withBaseDamage(0D),
                        emptyDamageType(damageType),
                        new DamageRequest(detachForWorker(resolvedContext.withBaseDamage(0D))),
                        seededRoll
                );
                debugDamagePlan(dodgedPlan);
                return dodgedPlan;
            }
        }
        DamageCalculationPlan plan = new DamageCalculationPlan(
                resolvedContext,
                effectiveDamageType,
                new DamageRequest(detachForWorker(resolvedContext)),
                seededRoll
        );
        debugDamagePlan(plan);
        return plan;
    }

    private ResolvedDamage finalizeResolvedDamage(DamageContext damageContext, DamageResult result) {
        if (!isResolvableDamageContext(damageContext) || result == null) {
            debugCombat(damageContext, "EVENT_BLOCKED", "combat_debug.event_result_empty");
            return null;
        }
        DamageContext resultContext = result.damageContext() == null
                ? damageContext
                : damageContext.withDamageTypeId(result.damageTypeId()).withVariables(result.variables());
        DamageResult effectiveResult = new DamageResult(
                result.damageTypeId(),
                result.finalDamage(),
                result.critical(),
                result.roll(),
                result.stageValues(),
                resultContext
        );
        if (shouldDebugCombat(resultContext)) {
            debugCombat(resultContext, "CALC_RESULT", "combat_debug.calc_result", Map.of(
                    "damage_type", effectiveResult.damageTypeId(),
                    "final_damage", service.combatDebug().formatNumber(effectiveResult.finalDamage()),
                    "critical", effectiveResult.critical(),
                    "roll", service.combatDebug().formatNumber(effectiveResult.roll()),
                    "stages", service.combatDebug().formatStageValues(effectiveResult.stageValues())
            ));
        }
        EmakiAttributeDamageEvent event = new EmakiAttributeDamageEvent(resultContext, effectiveResult);
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
        DamageTypeDefinition damageType = resolveDamageType(effectiveResult.damageTypeId());
        if (shouldDebugCombat(resultContext)) {
            debugCombat(resultContext, "EVENT_PASSED", "combat_debug.event_passed", Map.of(
                    "final_damage", service.combatDebug().formatNumber(event.getFinalDamage()),
                    "resolved_damage_type", damageType.id()
            ));
        }
        debugDamageFinal(resultContext, effectiveResult, damageType, event.getFinalDamage());
        return new ResolvedDamage(resultContext, effectiveResult, damageType, event.getFinalDamage());
    }

    private DamageTypeDefinition filterDamageType(DamageTypeDefinition damageType, boolean allowCritical, boolean calculateTargetDefense) { return new DamageCalculationServiceDelegate(this).filterDamageType(damageType, allowCritical, calculateTargetDefense); }

    private DamageTypeDefinition emptyDamageType(DamageTypeDefinition damageType) { return new DamageCalculationServiceDelegate(this).emptyDamageType(damageType); }

    double readSnapshotAttribute(AttributeSnapshot snapshot, String attributeId) { return new DamageCalculationServiceDelegate(this).readSnapshotAttribute(snapshot, attributeId); }

    void applyRecovery(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageResult result,
            double finalDamage) { new DamageCalculationServiceDelegate(this).applyRecovery(damageContext, damageType, result, finalDamage); }

    void notifyDamageMessages(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageResult result,
            double finalDamage) { new DamageCalculationServiceDelegate(this).notifyDamageMessages(damageContext, damageType, result, finalDamage); }

    <T> CompletableFuture<T> callEntityOwner(UUID entityId, Function<LivingEntity, T> operation) { return new DamageCalculationServiceDelegate(this).callEntityOwner(entityId, operation); }

    EmakiScheduling scheduling() { return new DamageCalculationServiceDelegate(this).scheduling(); }

    private UUID parseUuid(String value) { return new DamageCalculationServiceDelegate(this).parseUuid(value); }

    private String firstNonBlank(String left, String right) { return new DamageCalculationServiceDelegate(this).firstNonBlank(left, right); }

    record TargetApplicationResult(boolean applied,
            int cooldownTicks,
            AttackerSideEffectsIntent attackerIntent) {

        static TargetApplicationResult failed() {
            return new TargetApplicationResult(false, 0, null);
        }
    }

    record AttackerSideEffectsIntent(UUID attackerId,
            AttributeSnapshot attackerSnapshot,
            double recoveryAmount,
            DamageMessageDispatcher.MessageIntent message,
            boolean player) {
    }

    record DamageCalculationPlan(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageRequest request,
            double seededRoll) {

    }

    private double resolveRecoveryAmount(DamageContext damageContext, RecoveryDefinition recovery, double finalDamage) { return new DamageCalculationServiceDelegate(this).resolveRecoveryAmount(damageContext, recovery, finalDamage); }

    AttributeSnapshot snapshotForRecovery(DamageContext damageContext, DamageStageSource source) { return new DamageCalculationServiceDelegate(this).snapshotForRecovery(damageContext, source); }

    double sumAttributes(AttributeSnapshot snapshot, Map<String, ?> context, List<String> ids) { return new DamageCalculationServiceDelegate(this).sumAttributes(snapshot, context, ids); }

    void debugDamagePlan(DamageCalculationPlan plan) { new DamageCalculationServiceDelegate(this).debugDamagePlan(plan); }

    void emitDamagePlanAnchors(DamageCalculationPlan plan, DamageContext context) {
        DamageTypeDefinition damageType = plan.damageType();
        Map<String, List<AttributeContributionTrace>> attackerTraces = debugContributionIndex(context.attacker());
        Map<String, List<AttributeContributionTrace>> targetTraces = debugContributionIndex(context.target());
        debugDamage(context, "combat.damage_base", Map.ofEntries(
                Map.entry("attacker", debugEntityLabel(context.attacker())),
                Map.entry("target", debugEntityLabel(context.target())),
                Map.entry("projectile", debugEntityLabel(context.projectile())),
                Map.entry("damage_type", Texts.toStringSafe(damageType == null ? "" : damageType.id())),
                Map.entry("cause", context.cause() == null ? DEBUG_NONE : context.cause().name()),
                Map.entry("source_damage", debugNumber(context.sourceDamage())),
                Map.entry("base_damage", debugNumber(context.baseDamage())),
                Map.entry("stage_chain", debugStageChain(damageType)),
                Map.entry("allow_critical", String.valueOf(context.variables().getBoolean(true, "allow_critical"))),
                Map.entry("calculate_target_defense",
                        String.valueOf(context.variables().getBoolean(true, "calculate_target_defense"))),
                Map.entry("allow_target_dodge", String.valueOf(context.variables().getBoolean(false, "allow_target_dodge"))),
                Map.entry("target_blocking", debugNumber(context.variables().getDouble(ShieldBlockResolver.TARGET_BLOCKING, 0D))),
                Map.entry("seeded_roll", debugNumber(plan.seededRoll())),
                Map.entry("attacker_signature", debugSignature(context.attackerSnapshot())),
                Map.entry("target_signature", debugSignature(context.targetSnapshot()))
        ));
        if (damageType == null) {
            return;
        }
        for (DamageStageDefinition stage : damageType.stages()) {
            if (stage == null) {
                continue;
            }
            Map<String, List<AttributeContributionTrace>> stageTraces = switch (stage.source()) {
                case ATTACKER ->
                    attackerTraces;
                case TARGET ->
                    targetTraces;
                case CONTEXT ->
                    null;
            };
            debugDamage(context, debugStageLangKey(stage), debugStageFields(context, plan, stage, stageTraces));
        }
    }

    private String debugStageLangKey(DamageStageDefinition stage) { return new DamageCalculationServiceDelegate(this).debugStageLangKey(stage); }

    private Map<String, ?> debugStageFields(DamageContext context,
            DamageCalculationPlan plan,
            DamageStageDefinition stage,
            Map<String, List<AttributeContributionTrace>> traces) {
        AttributeSnapshot snapshot = switch (stage.source()) {
            case ATTACKER ->
                context.attackerSnapshot();
            case TARGET ->
                context.targetSnapshot();
            case CONTEXT ->
                null;
        };
        DamageContextVariables variables = context.variables();
        return Map.ofEntries(
                Map.entry("attacker", debugEntityLabel(context.attacker())),
                Map.entry("target", debugEntityLabel(context.target())),
                Map.entry("damage_type", Texts.toStringSafe(plan.damageType() == null ? "" : plan.damageType().id())),
                Map.entry("stage", Texts.toStringSafe(stage.id())),
                Map.entry("stage_kind", String.valueOf(stage.kind())),
                Map.entry("stage_source", String.valueOf(stage.source())),
                Map.entry("stage_mode", String.valueOf(stage.mode())),
                Map.entry("chain_base_damage", debugNumber(context.baseDamage())),
                Map.entry("flat_sources", debugContributions(snapshot, variables, traces, stage.flatAttributes())),
                Map.entry("percent_sources", debugContributions(snapshot, variables, traces, stage.percentAttributes())),
                Map.entry("chance_sources", debugContributions(snapshot, variables, traces, stage.chanceAttributes())),
                Map.entry("multiplier_sources", debugContributions(snapshot, variables, traces, stage.multiplierAttributes())),
                Map.entry("expression", Texts.isBlank(stage.expression()) ? DEBUG_NONE : stage.expression()),
                Map.entry("clamps", debugClamps(stage)),
                Map.entry("target_blocking", debugNumber(variables.getDouble(ShieldBlockResolver.TARGET_BLOCKING, 0D))),
                Map.entry("roll_flat", debugNumber(variables.getDouble("damage_roll_flat", 0D))),
                Map.entry("roll_percent", debugNumber(variables.getDouble("damage_roll_percent", 0D))),
                Map.entry("roll_chance", debugNumber(variables.getDouble("damage_roll_chance", 0D))),
                Map.entry("roll_multiplier", debugNumber(variables.getDouble("damage_roll_multiplier", 0D))),
                Map.entry("seeded_roll", debugNumber(plan.seededRoll())),
                Map.entry("source_signature", debugSignature(snapshot))
        );
    }

    private String debugStageChain(DamageTypeDefinition damageType) { return new DamageCalculationServiceDelegate2(this).debugStageChain(damageType); }

    private String debugClamps(DamageStageDefinition stage) { return new DamageCalculationServiceDelegate2(this).debugClamps(stage); }

    String debugBound(Double value) { return new DamageCalculationServiceDelegate2(this).debugBound(value); }

    private String debugContributions(AttributeSnapshot snapshot,
            DamageContextVariables variables,
            Map<String, List<AttributeContributionTrace>> traces,
            List<String> attributeIds) { return new DamageCalculationServiceDelegate2(this).debugContributions(snapshot, variables, traces, attributeIds); }

    String debugAttributeSources(Map<String, List<AttributeContributionTrace>> traces, String attributeId) { return new DamageCalculationServiceDelegate2(this).debugAttributeSources(traces, attributeId); }

    private Map<String, List<AttributeContributionTrace>> debugContributionIndex(LivingEntity entity) { return new DamageCalculationServiceDelegate2(this).debugContributionIndex(entity); }

    private String debugEntityLabel(Entity entity) { return new DamageCalculationServiceDelegate2(this).debugEntityLabel(entity); }

    private String debugSignature(AttributeSnapshot snapshot) { return new DamageCalculationServiceDelegate2(this).debugSignature(snapshot); }

    String debugField(String value) { return new DamageCalculationServiceDelegate2(this).debugField(value); }

    String debugNumber(double value) { return new DamageCalculationServiceDelegate2(this).debugNumber(value); }

    private void debugDamageFinal(DamageContext context,
            DamageResult engineResult,
            DamageTypeDefinition damageType,
            double eventFinalDamage) {
        if (!shouldDebugDamage(context)) {
            return;
        }
        try {
            debugDamage(context, "combat.damage_final", Map.ofEntries(
                    Map.entry("attacker", debugEntityLabel(context.attacker())),
                    Map.entry("target", debugEntityLabel(context.target())),
                    Map.entry("damage_type", Texts.toStringSafe(damageType == null ? "" : damageType.id())),
                    Map.entry("cause", context.cause() == null ? DEBUG_NONE : context.cause().name()),
                    Map.entry("base_damage", debugNumber(context.baseDamage())),
                    Map.entry("stage_outputs", debugStageOutputs(engineResult)),
                    Map.entry("critical", String.valueOf(engineResult != null && engineResult.critical())),
                    Map.entry("seeded_roll", debugNumber(engineResult == null ? 0D : engineResult.roll())),
                    Map.entry("engine_final_damage", debugNumber(engineResult == null ? 0D : engineResult.finalDamage())),
                    Map.entry("event_final_damage", debugNumber(eventFinalDamage)),
                    Map.entry("attacker_signature", debugSignature(context.attackerSnapshot())),
                    Map.entry("target_signature", debugSignature(context.targetSnapshot()))
            ));
        } catch (RuntimeException | LinkageError exception) {
            if (service.plugin() != null) {
                service.plugin().getLogger().log(
                        Level.WARNING,
                        "[combat] 伤害调试锚点输出失败；伤害计算不受影响",
                        exception
                );
            }
        }
    }

    private String debugStageOutputs(DamageResult result) { return new DamageCalculationServiceDelegate2(this).debugStageOutputs(result); }

    boolean shouldDebugDamage(DamageContext damageContext) { return new DamageCalculationServiceDelegate2(this).shouldDebugDamage(damageContext); }

    Player debugDamageSubject(DamageContext damageContext) { return new DamageCalculationServiceDelegate2(this).debugDamageSubject(damageContext); }

    private void debugDamage(DamageContext damageContext, String langKey, Map<String, ?> replacements) { new DamageCalculationServiceDelegate2(this).debugDamage(damageContext, langKey, replacements); }

    boolean shouldDebugCombat(DamageContext damageContext) { return new DamageCalculationServiceDelegate2(this).shouldDebugCombat(damageContext); }

    boolean shouldDebugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile) { return new DamageCalculationServiceDelegate2(this).shouldDebugCombat(attacker, target, projectile); }

    void debugCombat(DamageContext damageContext, String phase, String messageKey) { new DamageCalculationServiceDelegate2(this).debugCombat(damageContext, phase, messageKey); }

    void debugCombat(DamageContext damageContext, String phase, String messageKey, Map<String, ?> replacements) { new DamageCalculationServiceDelegate2(this).debugCombat(damageContext, phase, messageKey, replacements); }

    void debugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile, String phase, String messageKey) { new DamageCalculationServiceDelegate2(this).debugCombat(attacker, target, projectile, phase, messageKey); }

    void debugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile, String phase, String messageKey, Map<String, ?> replacements) { new DamageCalculationServiceDelegate2(this).debugCombat(attacker, target, projectile, phase, messageKey, replacements); }

}
