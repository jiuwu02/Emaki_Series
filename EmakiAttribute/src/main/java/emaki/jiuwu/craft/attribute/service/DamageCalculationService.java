package emaki.jiuwu.craft.attribute.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
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
        service.stateRepositoryInternal().setDamageTypeOverride(entity, damageTypeId);
    }

    public String peekDamageTypeOverride(LivingEntity entity) {
        return service.stateRepositoryInternal().peekDamageTypeOverride(entity);
    }

    public String consumeDamageTypeOverride(LivingEntity entity) {
        return service.stateRepositoryInternal().consumeDamageTypeOverride(entity);
    }

    public void markSyntheticDamage(LivingEntity entity, boolean value) {
        service.stateRepositoryInternal().markSyntheticDamage(entity, value);
    }

    public boolean isSyntheticDamage(LivingEntity entity) {
        return service.stateRepositoryInternal().isSyntheticDamage(entity);
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
        service.stateRepositoryInternal().writeProjectileSnapshot(projectile, snapshot);
        debugCombat(shooter, null, projectile, "PROJECTILE_SNAPSHOT_WRITE",
                "投射物快照已写入: projectile=" + entityDebugLabel(projectile)
                + ", shooter=" + entityDebugLabel(shooter)
                + ", damageType=" + snapshot.damageTypeId()
                + ", signature=" + snapshot.sourceSignature()
                + ", snapshot=" + formatSnapshot(snapshot.attackSnapshot()));
        return snapshot;
    }

    public ProjectileDamageSnapshot readProjectileSnapshot(Projectile projectile) {
        return service.stateRepositoryInternal().readProjectileSnapshot(projectile);
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
        String attackerLabel = entityLabel(attacker, cause, messageOrFallback("damage.environment", "环境"));
        String targetLabel = entityLabel(target, null, messageOrFallback("damage.target", "目标"));
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
            debugCombat(damageContext, "PROJECTILE_SNAPSHOT_MISSING",
                    "applyProjectileDamage 读取不到投射物快照，已中止结算: projectile=" + entityDebugLabel(projectile));
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
        service.stateRepositoryInternal().setDamageTypeOverride(player, null);
    }

    public ResolvedDamage resolveDamageApplication(DamageContext damageContext) {
        if (!isResolvableDamageContext(damageContext)) {
            debugCombat(damageContext, "RESOLVE_SKIPPED",
                    "目标无效或已死亡，EA 伤害解析已跳过。");
            return null;
        }
        debugCombat(damageContext, "RESOLVE_BEGIN",
                "开始解析 EA 伤害: " + describeDamageContext(damageContext)
                + " | attackerSnapshot=" + formatSnapshot(damageContext.attackerSnapshot())
                + " | targetSnapshot=" + formatSnapshot(damageContext.targetSnapshot()));
        DamageCalculationPlan plan = prepareDamageCalculation(damageContext);
        if (plan == null) {
            return null;
        }
        DamageResult result = service.damageEngine().resolve(plan.request(), plan.damageType(), plan.seededRoll());
        return finalizeResolvedDamage(plan.damageContext(), result);
    }

    public CompletableFuture<ResolvedDamage> resolveDamageApplicationAsync(DamageContext damageContext) {
        if (!isResolvableDamageContext(damageContext)) {
            debugCombat(damageContext, "RESOLVE_SKIPPED",
                    "目标无效或已死亡，EA 伤害解析已跳过。");
            return CompletableFuture.completedFuture(null);
        }
        debugCombat(damageContext, "RESOLVE_BEGIN",
                "开始异步解析 EA 伤害: " + describeDamageContext(damageContext)
                + " | attackerSnapshot=" + formatSnapshot(damageContext.attackerSnapshot())
                + " | targetSnapshot=" + formatSnapshot(damageContext.targetSnapshot()));
        DamageCalculationPlan plan = prepareDamageCalculation(damageContext);
        if (plan == null) {
            return CompletableFuture.completedFuture(null);
        }
        return service.asyncDamageEngineInternal()
                .resolveAsync(plan.request(), plan.damageType(), plan.seededRoll())
                .thenCompose(result -> {
                    if (service.asyncTaskSchedulerInternal() == null) {
                        return CompletableFuture.completedFuture(finalizeResolvedDamage(plan.damageContext(), result));
                    }
                    return service.asyncTaskSchedulerInternal().callSync(
                            "attribute-damage-finalize",
                            () -> finalizeResolvedDamage(plan.damageContext(), result)
                    );
                });
    }

    public boolean applyResolvedDamage(ResolvedDamage resolvedDamage, Entity visualSource, double alreadyAppliedDamage) {
        if (resolvedDamage == null || resolvedDamage.damageContext() == null) {
            return false;
        }
        DamageContext damageContext = resolvedDamage.damageContext();
        LivingEntity target = damageContext.target();
        if (target == null || !target.isValid() || target.isDead()) {
            debugCombat(damageContext, "APPLY_SKIPPED",
                    "目标无效或已死亡，EA 伤害落地已跳过。");
            return false;
        }
        double appliedDamage = Math.max(0D, alreadyAppliedDamage);
        double remainingDamage = Math.max(0D, resolvedDamage.finalDamage() - appliedDamage);
        double healthBefore = target.getHealth();
        double absorptionBefore = target.getAbsorptionAmount();
        debugCombat(damageContext, "APPLY_BEGIN",
                "准备落地 EA 伤害: finalDamage=" + formatNumber(resolvedDamage.finalDamage())
                + ", alreadyApplied=" + formatNumber(appliedDamage)
                + ", remaining=" + formatNumber(remainingDamage)
                + ", targetHealthBefore=" + formatNumber(healthBefore)
                + ", targetAbsorptionBefore=" + formatNumber(absorptionBefore)
                + ", visualSource=" + entityDebugLabel(visualSource));
        applyDirectDamage(target, remainingDamage, visualSource);
        applyAggroTarget(target, damageContext.attacker());
        int cooldownTicks = 0;
        if (damageContext.attacker() instanceof Player player) {
            cooldownTicks = service.startAttackCooldown(player, damageContext.attackerSnapshot(), player.getInventory().getItemInMainHand());
        }
        applyRecovery(damageContext, resolvedDamage.damageType(), resolvedDamage.damageResult(), resolvedDamage.finalDamage());
        notifyDamageMessages(damageContext, resolvedDamage.damageType(), resolvedDamage.damageResult(), resolvedDamage.finalDamage());
        service.scheduleHealthSync(target);
        debugCombat(damageContext, "APPLY_DONE",
                "EA 伤害已落地: targetHealthAfter=" + formatNumber(target.getHealth())
                + ", targetAbsorptionAfter=" + formatNumber(target.getAbsorptionAmount())
                + ", attackerCooldownTicks=" + cooldownTicks);
        return remainingDamage > 0D || appliedDamage > 0D;
    }

    private void applyAggroTarget(LivingEntity target, LivingEntity attacker) {
        if (!(target instanceof Mob mob) || attacker == null || !attacker.isValid() || attacker.isDead()
                || !target.isValid() || target.isDead() || target.getUniqueId().equals(attacker.getUniqueId())) {
            return;
        }
        mob.setAware(true);
        mob.setAggressive(true);
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
        double seededRoll = ThreadLocalRandom.current().nextDouble(0D, 100D);
        return new DamageCalculationPlan(
                resolvedContext,
                damageType,
                new DamageRequest(resolvedContext),
                seededRoll
        );
    }

    private ResolvedDamage finalizeResolvedDamage(DamageContext damageContext, DamageResult result) {
        if (!isResolvableDamageContext(damageContext) || result == null) {
            debugCombat(damageContext, "EVENT_BLOCKED",
                    "EA 伤害结果为空，已跳过后续事件与落地。");
            return null;
        }
        debugCombat(damageContext, "CALC_RESULT",
                "伤害计算完成: damageType=" + result.damageTypeId()
                + ", finalDamage=" + formatNumber(result.finalDamage())
                + ", critical=" + result.critical()
                + ", roll=" + formatNumber(result.roll())
                + ", stages=" + formatStageValues(result.stageValues()));
        EmakiAttributeDamageEvent event = new EmakiAttributeDamageEvent(damageContext, result);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getFinalDamage() <= 0D) {
            debugCombat(damageContext, "EVENT_BLOCKED",
                    "EmakiAttributeDamageEvent 未通过: cancelled=" + event.isCancelled()
                    + ", finalDamage=" + formatNumber(event.getFinalDamage()));
            return null;
        }
        DamageTypeDefinition damageType = resolveDamageType(result.damageTypeId());
        debugCombat(damageContext, "EVENT_PASSED",
                "EmakiAttributeDamageEvent 已通过: finalDamage=" + formatNumber(event.getFinalDamage())
                + ", resolvedDamageType=" + damageType.id());
        return new ResolvedDamage(damageContext, result, damageType, event.getFinalDamage());
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
        player.sendMessage(MiniMessages.parse(rendered));
    }

    private Map<String, Object> buildDamageMessageReplacements(DamageContext damageContext,
            DamageTypeDefinition damageType,
            DamageResult result,
            double finalDamage) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        String attackerLabel = entityLabel(damageContext.attacker(), damageContext.cause(), messageOrFallback("damage.environment", "环境"));
        String targetLabel = entityLabel(damageContext.target(), null, messageOrFallback("damage.target", "目标"));
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
        replacements.put("critical_text", result.critical() ? messageOrFallback("damage.critical_text", "暴击") : "");
        replacements.put("critical_suffix", result.critical() ? messageOrFallback("damage.critical_suffix", " <red>暴击</red>") : "");
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
            return messageOrFallback("damage.cause.environment", "环境");
        }
        return switch (cause) {
            case CONTACT ->
                messageOrFallback("damage.cause.contact", "接触");
            case ENTITY_ATTACK ->
                messageOrFallback("damage.cause.entity_attack", "攻击");
            case PROJECTILE ->
                messageOrFallback("damage.cause.projectile", "弹射物");
            case SUFFOCATION ->
                messageOrFallback("damage.cause.suffocation", "窒息");
            case FALL ->
                messageOrFallback("damage.cause.fall", "摔落");
            case FIRE ->
                messageOrFallback("damage.cause.fire", "火焰");
            case FIRE_TICK ->
                messageOrFallback("damage.cause.fire_tick", "燃烧");
            case MELTING ->
                messageOrFallback("damage.cause.melting", "融化");
            case LAVA ->
                messageOrFallback("damage.cause.lava", "岩浆");
            case DROWNING ->
                messageOrFallback("damage.cause.drowning", "溺水");
            case BLOCK_EXPLOSION ->
                messageOrFallback("damage.cause.block_explosion", "方块爆炸");
            case ENTITY_EXPLOSION ->
                messageOrFallback("damage.cause.entity_explosion", "爆炸");
            case VOID ->
                messageOrFallback("damage.cause.void", "虚空");
            case LIGHTNING ->
                messageOrFallback("damage.cause.lightning", "雷击");
            case WORLD_BORDER ->
                messageOrFallback("damage.cause.world_border", "世界边界");
            case STARVATION ->
                messageOrFallback("damage.cause.starvation", "饥饿");
            case POISON ->
                messageOrFallback("damage.cause.poison", "中毒");
            case MAGIC ->
                messageOrFallback("damage.cause.magic", "魔法");
            case WITHER ->
                messageOrFallback("damage.cause.wither", "凋零");
            case FALLING_BLOCK ->
                messageOrFallback("damage.cause.falling_block", "落块");
            case DRAGON_BREATH ->
                messageOrFallback("damage.cause.dragon_breath", "龙息");
            case FLY_INTO_WALL ->
                messageOrFallback("damage.cause.fly_into_wall", "碰撞");
            case HOT_FLOOR ->
                messageOrFallback("damage.cause.hot_floor", "高温");
            case CAMPFIRE ->
                messageOrFallback("damage.cause.campfire", "营火");
            case CRAMMING ->
                messageOrFallback("damage.cause.cramming", "挤压");
            case DRYOUT ->
                messageOrFallback("damage.cause.dryout", "脱水");
            case FREEZE ->
                messageOrFallback("damage.cause.freeze", "冻结");
            case SONIC_BOOM ->
                messageOrFallback("damage.cause.sonic_boom", "音爆");
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

    private void debugCombat(DamageContext damageContext, String phase, String detail) {
        if (damageContext == null) {
            debugCombat((LivingEntity) null, null, null, phase, detail);
            return;
        }
        debugCombat(damageContext.attacker(), damageContext.target(), damageContext.projectile(), phase, detail);
    }

    private void debugCombat(LivingEntity attacker, LivingEntity target, Projectile projectile, String phase, String detail) {
        var debugService = service.combatDebugService();
        boolean enabled = projectile != null
                ? debugService.shouldTrace(projectile, target)
                : debugService.shouldTrace(attacker, target);
        if (!enabled) {
            return;
        }
        debugService.log(phase, detail);
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
