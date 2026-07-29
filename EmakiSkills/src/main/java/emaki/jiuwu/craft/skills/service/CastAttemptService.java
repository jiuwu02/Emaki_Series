package emaki.jiuwu.craft.skills.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.event.SkillPreCastEvent;
import emaki.jiuwu.craft.skills.bridge.EaBridge;
import emaki.jiuwu.craft.skills.bridge.ExternalManaBridge;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.model.CastAttemptResult;
import emaki.jiuwu.craft.skills.model.CastAttemptResult.FailureReason;
import emaki.jiuwu.craft.skills.model.CostOperation;
import emaki.jiuwu.craft.skills.model.LocalResourceDefinition;
import emaki.jiuwu.craft.skills.model.PlayerCastTimingState;
import emaki.jiuwu.craft.skills.model.PlayerLocalResourceState;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.ResolvedSkillParameters;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillResourceCost;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.mythic.MythicSkillCastService;
import emaki.jiuwu.craft.skills.script.SkillScriptCastService;
import emaki.jiuwu.craft.skills.script.SkillScriptMode;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

public final class CastAttemptService {

    private final EmakiSkillsPlugin plugin;
    private final PlayerSkillStateService stateService;
    private final CastModeService castModeService;
    private final PlayerSkillDataStore dataStore;
    private final MythicSkillCastService mythicCastService;
    private final SkillScriptCastService skillScriptCastService;
    private final SkillParameterResolver skillParameterResolver;
    private final EaBridge eaBridge;
    private final ExternalManaBridge externalManaBridge;
    private final Supplier<Map<String, LocalResourceDefinition>> localResourceDefsSupplier;
    private final Supplier<AppConfig> configSupplier;
    private final Map<CastKey, CompletableFuture<CastAttemptResult>> inFlight = new ConcurrentHashMap<>();

    public CastAttemptService(EmakiSkillsPlugin plugin,
            PlayerSkillStateService stateService,
            CastModeService castModeService,
            PlayerSkillDataStore dataStore,
            MythicSkillCastService mythicCastService,
            SkillScriptCastService skillScriptCastService,
            SkillParameterResolver skillParameterResolver,
            EaBridge eaBridge,
            ExternalManaBridge externalManaBridge,
            Supplier<Map<String, LocalResourceDefinition>> localResourceDefsSupplier,
            Supplier<AppConfig> configSupplier) {
        this.plugin = plugin;
        this.stateService = stateService;
        this.castModeService = castModeService;
        this.dataStore = dataStore;
        this.mythicCastService = mythicCastService;
        this.skillScriptCastService = skillScriptCastService;
        this.skillParameterResolver = skillParameterResolver;
        this.eaBridge = eaBridge;
        this.externalManaBridge = externalManaBridge;
        this.localResourceDefsSupplier = localResourceDefsSupplier;
        this.configSupplier = configSupplier;
    }

    public CompletableFuture<CastAttemptResult> attemptCast(Player player, String triggerId) {
        if (player == null || Texts.isBlank(triggerId)) {
            return completedFailure(FailureReason.NO_BINDING, "cast.invalid_input");
        }
        return onCaster(player, () -> CompletableFuture.completedFuture(prepareBoundAttempt(player, triggerId, null)))
                .thenCompose(this::executePlan);
    }

    public CompletableFuture<CastAttemptResult> attemptCast(Player player,
            String triggerId,
            SkillSlotBinding binding) {
        if (player == null || Texts.isBlank(triggerId)) {
            return completedFailure(FailureReason.NO_BINDING, "cast.invalid_input");
        }
        if (binding == null || binding.isEmpty()) {
            return completedFailure(FailureReason.NO_BINDING, "cast.no_binding");
        }
        return onCaster(player, () -> CompletableFuture.completedFuture(prepareBoundAttempt(player, triggerId, binding)))
                .thenCompose(this::executePlan);
    }

    public CompletableFuture<CastAttemptResult> attemptPassiveCast(Player player,
            String triggerId,
            SkillDefinition definition,
            TriggerInvocation invocation) {
        if (player == null || Texts.isBlank(triggerId)) {
            return completedFailure(FailureReason.NO_BINDING, "cast.invalid_input");
        }
        return onCaster(player, () -> CompletableFuture.completedFuture(
                        prepareDefinitionAttempt(player, definition, triggerId, invocation, false)))
                .thenCompose(this::executePlan);
    }

    public CompletableFuture<CastAttemptResult> attemptDirectCast(Player player,
            String triggerId,
            SkillDefinition definition,
            TriggerInvocation invocation) {
        if (player == null || Texts.isBlank(triggerId)) {
            return completedFailure(FailureReason.NO_BINDING, "cast.invalid_input");
        }
        return onCaster(player, () -> CompletableFuture.completedFuture(
                        prepareDefinitionAttempt(player, definition, triggerId, invocation, true)))
                .thenCompose(this::executePlan);
    }

    private AttemptPlan prepareBoundAttempt(Player player, String triggerId, SkillSlotBinding suppliedBinding) {
        if (!castModeService.isCastModeEnabled(player)) {
            return AttemptPlan.failure(player, FailureReason.NOT_IN_CAST_MODE, "cast.not_in_cast_mode");
        }
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return AttemptPlan.failure(player, FailureReason.NO_BINDING, "cast.no_profile");
        }
        SkillSlotBinding binding = suppliedBinding == null ? findBindingByTrigger(profile, triggerId) : suppliedBinding;
        if (binding == null || binding.isEmpty()) {
            return AttemptPlan.failure(player, FailureReason.NO_BINDING, "cast.no_binding");
        }
        SkillDefinition definition = stateService.getDefinition(binding.skillId());
        if (definition == null) {
            return AttemptPlan.failure(player, FailureReason.SKILL_NOT_FOUND, "skill.not_found");
        }
        List<UnlockedSkillEntry> unlocked = stateService.getUnlockedSkills(player);
        boolean inPool = unlocked.stream().anyMatch(entry -> entry.skillId().equals(binding.skillId()));
        if (!inPool) {
            return AttemptPlan.failure(player, FailureReason.SOURCE_LOST, "skill.source_lost");
        }
        return AttemptPlan.ready(player, definition, triggerId, null);
    }

    private AttemptPlan prepareDefinitionAttempt(Player player,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            boolean requireActive) {
        if (definition == null || !definition.enabled()
                || (requireActive && definition.activationType()
                        != emaki.jiuwu.craft.skills.model.SkillActivationType.ACTIVE)) {
            return AttemptPlan.failure(player, FailureReason.SKILL_NOT_FOUND, "skill.not_found");
        }
        return AttemptPlan.ready(player, definition, triggerId, invocation);
    }

    private CompletableFuture<CastAttemptResult> executePlan(AttemptPlan plan) {
        if (plan.failure() != null) {
            return onCaster(plan.player(), () -> CompletableFuture.completedFuture(plan.failure()));
        }

        CompletableFuture<CastAttemptResult> gate = new CompletableFuture<>();
        CompletableFuture<CastAttemptResult> existing = inFlight.putIfAbsent(plan.key(), gate);
        if (existing != null) {
            return onCaster(plan.player(), () -> {
                PlayerSkillProfile profile = dataStore.get(plan.player());
                long until = profile == null ? 0L : profile.timingState().forcedGlobalCastDelayUntil();
                return completedFailure(
                        FailureReason.FORCED_DELAY_ACTIVE,
                        "cast.forced_delay",
                        cooldownReplacements(until, plan.definition()));
            });
        }

        CompletableFuture<CastAttemptResult> attempt = onCaster(plan.player(), () -> attemptOnDomain(plan));
        attempt.whenComplete((result, throwable) -> {
            inFlight.remove(plan.key(), gate);
            if (throwable != null) {
                gate.complete(CastAttemptResult.fail(
                        FailureReason.MYTHIC_CAST_FAILED, "cast.skill_execute_failed"));
            } else {
                gate.complete(result == null
                        ? CastAttemptResult.fail(FailureReason.MYTHIC_CAST_FAILED, "cast.skill_execute_failed")
                        : result);
            }
        });
        return gate;
    }

    private CompletionStage<CastAttemptResult> attemptOnDomain(AttemptPlan plan) {
        Player player = plan.player();
        SkillDefinition definition = plan.definition();
        PlayerSkillDataStore.SessionTicket session = dataStore.currentSession(player.getUniqueId());
        PlayerSkillProfile profile = dataStore.get(session);
        if (profile == null) {
            return completedFailure(FailureReason.NO_BINDING, "cast.no_profile");
        }

        PlayerCastTimingState timing = profile.timingState();
        if (timing.isForcedDelayActive()) {
            return completedFailure(
                    FailureReason.FORCED_DELAY_ACTIVE,
                    "cast.forced_delay",
                    cooldownReplacements(timing.forcedGlobalCastDelayUntil(), definition));
        }
        if (timing.isGlobalCooldownActive()) {
            return completedFailure(
                    FailureReason.GLOBAL_COOLDOWN_ACTIVE,
                    "cast.global_cooldown",
                    cooldownReplacements(timing.globalCooldownUntil(), definition));
        }
        if (timing.isSkillOnCooldown(definition.id())) {
            Long until = timing.skillCooldownUntilBySkillId().get(definition.id());
            return completedFailure(
                    FailureReason.SKILL_COOLDOWN_ACTIVE,
                    "cast.skill_cooldown",
                    cooldownReplacements(until == null ? 0L : until, definition));
        }

        if (!definition.conditions().emptyGroup()) {
            boolean conditionsPassed = ConditionEvaluator.evaluate(
                    definition.conditions(),
                    text -> PlaceholderRenderer.renderPapi(player, text, null, "skill_cast"),
                    true,
                    ConditionContext.of(player, null, Map.of(
                            "skillId", definition.id(),
                            "triggerId", Texts.toStringSafe(plan.triggerId()))));
            if (!conditionsPassed) {
                return completedFailure(FailureReason.RESOURCE_INSUFFICIENT, "cast.condition_not_met");
            }
        }

        CastAttemptResult costCheck = checkResourceCosts(player, profile, definition);
        if (costCheck != null) {
            return CompletableFuture.completedFuture(costCheck);
        }

        ResolvedSkillParameters parameters = skillParameterResolver == null
                ? ResolvedSkillParameters.empty()
                : skillParameterResolver.resolve(player, definition, plan.triggerId(), plan.invocation());
        SkillPreCastEvent preCastEvent = new SkillPreCastEvent(
                player, definition.id(), Texts.toStringSafe(plan.triggerId()));
        plugin.getServer().getPluginManager().callEvent(preCastEvent);
        if (preCastEvent.isCancelled()) {
            return completedFailure(FailureReason.CANCELLED, "cast.cancelled");
        }

        return castSkillAsync(player, definition, plan.triggerId(), plan.invocation(), parameters)
                .handle((success, throwable) -> new CastOutcome(Boolean.TRUE.equals(success), throwable))
                .thenCompose(outcome -> onCaster(player, () -> CompletableFuture.completedFuture(
                        finalizeAttempt(player, session, definition, outcome))));
    }

    private CastAttemptResult finalizeAttempt(Player player,
            PlayerSkillDataStore.SessionTicket session,
            SkillDefinition definition,
            CastOutcome outcome) {
        if (outcome.throwable() != null || !outcome.success()) {
            return CastAttemptResult.fail(FailureReason.MYTHIC_CAST_FAILED, "cast.skill_execute_failed");
        }
        AppConfig config = configSupplier.get();
        long forcedDelayTicks = config != null ? config.castTiming().forcedGlobalCastDelayTicks() : 0L;
        boolean committed = dataStore.mutateIfCurrent(session, profile -> {
            consumeResources(player, profile, definition);
            profile.timingState().recordCast(
                    definition.id(),
                    definition.cooldownTicks(),
                    definition.globalCooldownTicks(),
                    forcedDelayTicks
            );
            profile.markDirty();
        });
        return committed
                ? CastAttemptResult.ok()
                : CastAttemptResult.fail(FailureReason.SOURCE_LOST, "skill.source_lost");
    }

    private CompletableFuture<Boolean> castSkillAsync(Player player,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            ResolvedSkillParameters parameters) {
        boolean hasScript = definition.script() != null && definition.script().enabled();
        String mythicSkillId = definition.mythicSkill();
        boolean hasMythic = Texts.isNotBlank(mythicSkillId);
        SkillScriptMode mode = hasScript ? definition.script().mode() : SkillScriptMode.MYTHIC;
        if (hasScript && mode == SkillScriptMode.NATIVE) {
            return skillScriptCastService == null
                    ? CompletableFuture.completedFuture(false)
                    : skillScriptCastService.cast(player, definition, triggerId, invocation, parameters);
        }
        if (hasScript && mode == SkillScriptMode.HYBRID) {
            if (skillScriptCastService == null) {
                return CompletableFuture.completedFuture(false);
            }
            return skillScriptCastService.cast(player, definition, triggerId, invocation, parameters)
                    .thenCompose(nativeOk -> {
                        if (!nativeOk) {
                            return CompletableFuture.completedFuture(false);
                        }
                        if (!hasMythic) {
                            return CompletableFuture.completedFuture(true);
                        }
                        return onCaster(player, () -> CompletableFuture.completedFuture(
                                castMythic(player, mythicSkillId, invocation, parameters)));
                    });
        }
        return CompletableFuture.completedFuture(
                hasMythic && castMythic(player, mythicSkillId, invocation, parameters));
    }

    private boolean castMythic(Player player,
            String mythicSkillId,
            TriggerInvocation invocation,
            ResolvedSkillParameters parameters) {
        return mythicCastService != null
                && mythicCastService.skillExists(mythicSkillId)
                && mythicCastService.cast(player, mythicSkillId, invocation, parameters);
    }

    private SkillSlotBinding findBindingByTrigger(PlayerSkillProfile profile, String triggerId) {
        SkillSlotBinding indexed = profile.findBindingByTrigger(triggerId);
        if (indexed != null) {
            return indexed;
        }
        for (SkillSlotBinding binding : profile.bindings()) {
            if (!binding.isEmpty() && triggerId.equals(binding.triggerId())) {
                return binding;
            }
        }
        return null;
    }

    private CastAttemptResult checkResourceCosts(Player player,
            PlayerSkillProfile profile,
            SkillDefinition definition) {
        for (SkillResourceCost cost : definition.resourceCosts()) {
            boolean sufficient = switch (cost.type()) {
                case EA_RESOURCE -> checkEaResource(player, cost);
                case ATTRIBUTE_CHECK -> checkAttribute(player, cost);
                case LOCAL_RESOURCE -> checkLocalResource(profile, cost);
                case AURASKILLS_MANA, MYTHICLIB_MANA -> checkExternalMana(player, cost);
            };
            if (!sufficient) {
                String message = Texts.isNotBlank(cost.failureMessage())
                        ? PlaceholderRenderer.renderPapi(player, cost.failureMessage(), null, "skill_cast")
                        : cost.targetId();
                return CastAttemptResult.fail(
                        FailureReason.RESOURCE_INSUFFICIENT,
                        "cast.resource_insufficient",
                        Map.of("message", message));
            }
        }
        return null;
    }

    private boolean checkEaResource(Player player, SkillResourceCost cost) {
        if (eaBridge == null || !eaBridge.isAvailable()) {
            return true;
        }
        return eaBridge.readResourceCurrent(player, cost.targetId()) >= cost.amount();
    }

    private boolean checkAttribute(Player player, SkillResourceCost cost) {
        if (eaBridge == null || !eaBridge.isAvailable()) {
            return true;
        }
        return eaBridge.readAttributeValue(player, cost.targetId()) >= cost.amount();
    }

    private boolean checkLocalResource(PlayerSkillProfile profile, SkillResourceCost cost) {
        PlayerLocalResourceState state = profile.localResources().get(cost.targetId());
        return state != null && state.currentValue() >= cost.amount();
    }

    private boolean checkExternalMana(Player player, SkillResourceCost cost) {
        return externalManaBridge != null
                && externalManaBridge.isAvailable(cost.type())
                && externalManaBridge.readCurrent(player, cost.type()) >= cost.amount();
    }

    private void consumeResources(Player player, PlayerSkillProfile profile, SkillDefinition definition) {
        for (SkillResourceCost cost : definition.resourceCosts()) {
            if (cost.operation() != CostOperation.CONSUME) {
                continue;
            }
            switch (cost.type()) {
                case EA_RESOURCE -> {
                    if (eaBridge != null && eaBridge.isAvailable()) {
                        eaBridge.consumeResource(player, cost.targetId(), cost.amount());
                    }
                }
                case LOCAL_RESOURCE -> {
                    PlayerLocalResourceState state = profile.localResources().get(cost.targetId());
                    if (state != null) {
                        state.setCurrentValue(Math.max(0D, state.currentValue() - cost.amount()));
                    }
                }
                case ATTRIBUTE_CHECK -> {
                }
                case AURASKILLS_MANA, MYTHICLIB_MANA -> {
                    if (externalManaBridge != null && externalManaBridge.isAvailable(cost.type())) {
                        externalManaBridge.consume(player, cost.type(), cost.amount());
                    }
                }
            }
        }
    }

    private <T> CompletableFuture<T> onCaster(Player player,
            Supplier<? extends CompletionStage<T>> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable operation = () -> {
            try {
                CompletionStage<T> stage = task.get();
                if (stage == null) {
                    future.completeExceptionally(new IllegalStateException(
                            "Cast entity-domain task returned no completion stage."));
                    return;
                }
                stage.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                    } else {
                        future.complete(result);
                    }
                });
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };
        try {
            if (plugin.threadOwnership() != null && plugin.threadOwnership().isEntityOwned(player)) {
                operation.run();
                return future;
            }
            var scheduled = plugin.executionDispatcher().runEntity(plugin, player, operation,
                    () -> future.completeExceptionally(new RejectedExecutionException(
                            "Cast entity-domain task retired before execution.")));
            if (scheduled == null) {
                future.completeExceptionally(new RejectedExecutionException(
                        "Cast entity-domain task scheduling was rejected."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private static Map<String, ?> cooldownReplacements(long until, SkillDefinition definition) {
        double remainingSeconds = Math.max(0L, until - System.currentTimeMillis()) / 1000D;
        String skill = definition == null || Texts.isBlank(definition.displayName())
                ? definition == null ? "" : definition.id()
                : definition.displayName();
        return Map.of(
                "remaining", Numbers.formatNumber(remainingSeconds, "0.#"),
                "skill", skill
        );
    }

    private static CompletableFuture<CastAttemptResult> completedFailure(FailureReason reason, String message) {
        return completedFailure(reason, message, Map.of());
    }

    private static CompletableFuture<CastAttemptResult> completedFailure(FailureReason reason,
            String message,
            Map<String, ?> replacements) {
        return CompletableFuture.completedFuture(CastAttemptResult.fail(reason, message, replacements));
    }

    private record CastKey(String playerUuid, String skillId) {
    }

    private record AttemptPlan(Player player,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            CastKey key,
            CastAttemptResult failure) {

        private static AttemptPlan ready(Player player,
                SkillDefinition definition,
                String triggerId,
                TriggerInvocation invocation) {
            return new AttemptPlan(
                    player,
                    definition,
                    triggerId,
                    invocation,
                    new CastKey(player.getUniqueId().toString(), definition.id()),
                    null);
        }

        private static AttemptPlan failure(Player player, FailureReason reason, String message) {
            return new AttemptPlan(player, null, "", null, null, CastAttemptResult.fail(reason, message));
        }
    }

    private record CastOutcome(boolean success, Throwable throwable) {
    }
}
