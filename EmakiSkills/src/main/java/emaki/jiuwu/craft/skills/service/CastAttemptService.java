package emaki.jiuwu.craft.skills.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.v2.exec.PipelineOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.event.SkillPostCastEvent;
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

    /** Reason key {@link #castMythic} reports for an absent Mythic skill. */
    static final String MYTHIC_MISSING_REASON = "skill.mythic_not_found";

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
                .handle((result, throwable) -> new CastOutcome(result, throwable))
                .thenCompose(outcome -> onCaster(player, () -> CompletableFuture.completedFuture(
                        finalizeAttempt(player, session, definition, plan.triggerId(), outcome))));
    }

    private CastAttemptResult finalizeAttempt(Player player,
            PlayerSkillDataStore.SessionTicket session,
            SkillDefinition definition,
            String triggerId,
            CastOutcome outcome) {
        if (outcome.throwable() != null || !outcome.success()) {
            return describeCastFailure(definition, triggerId, outcome);
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
        if (!committed) {
            return CastAttemptResult.fail(FailureReason.SOURCE_LOST, "skill.source_lost");
        }
        plugin.getServer().getPluginManager().callEvent(
                new SkillPostCastEvent(player, definition.id(), Texts.toStringSafe(triggerId)));
        return CastAttemptResult.ok(definition.id(), Texts.toStringSafe(triggerId));
    }

    private CompletableFuture<PipelineOutcome> castSkillAsync(Player player,
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
                    ? CompletableFuture.completedFuture(scriptServiceUnavailable())
                    : skillScriptCastService.cast(player, definition, triggerId, invocation, parameters);
        }
        if (hasScript && mode == SkillScriptMode.HYBRID) {
            if (skillScriptCastService == null) {
                return CompletableFuture.completedFuture(scriptServiceUnavailable());
            }
            return skillScriptCastService.cast(player, definition, triggerId, invocation, parameters)
                    .thenCompose(nativeOutcome -> {
                        if (nativeOutcome == null) {
                            return CompletableFuture.completedFuture(scriptServiceUnavailable());
                        }
                        if (nativeOutcome.status() == PipelineOutcome.Status.FAILURE) {
                            return CompletableFuture.completedFuture(nativeOutcome);
                        }
                        if (!hasMythic) {
                            return CompletableFuture.completedFuture(nativeOutcome);
                        }
                        return onCaster(player, () -> CompletableFuture.completedFuture(
                                castMythic(player, mythicSkillId, invocation, parameters)));
                    });
        }
        if (!hasMythic) {
            // REJECTED rather than INVALID_CONFIG so this keeps landing on `cast.skill_execute_failed`, which
            // is the message v1's INVALID_STATE produced for the same "not castable at all" case.
            return CompletableFuture.completedFuture(PipelineOutcome.failure(
                    CoreActionFailureKind.REJECTED, "skill.mythic_not_configured",
                    Map.of(), List.of()));
        }
        return CompletableFuture.completedFuture(castMythic(player, mythicSkillId, invocation, parameters));
    }

    private static PipelineOutcome scriptServiceUnavailable() {
        return PipelineOutcome.failure(CoreActionFailureKind.OWNER_DISABLED,
                "skill.script_unavailable", Map.of(), List.of());
    }

    /**
     * Casts the Mythic skill and reports which stage failed, so a missing skill id
     * is no longer indistinguishable from a Mythic-side execution failure.
     *
     * <p>Mythic does not go through the pipeline, so the outcome is constructed directly. The failure kinds are
     * chosen to land on the same message keys the v1 error types did: a missing skill is a configuration
     * mistake, a refusal is not.</p>
     */
    private PipelineOutcome castMythic(Player player,
            String mythicSkillId,
            TriggerInvocation invocation,
            ResolvedSkillParameters parameters) {
        if (mythicCastService == null) {
            return PipelineOutcome.failure(CoreActionFailureKind.OWNER_DISABLED,
                    "skill.script_unavailable", Map.of(), List.of());
        }
        if (!mythicCastService.skillExists(mythicSkillId)) {
            return PipelineOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    MYTHIC_MISSING_REASON,
                    Map.of("mythic_skill", Texts.toStringSafe(mythicSkillId)), List.of());
        }
        return mythicCastService.cast(player, mythicSkillId, invocation, parameters)
                ? PipelineOutcome.success(List.of())
                : PipelineOutcome.failure(CoreActionFailureKind.REJECTED,
                        "skill.mythic_rejected", Map.of(), List.of());
    }

    /**
     * Maps a failed script or Mythic cast onto a specific message key.
     *
     * <p>Every failure used to collapse into {@code cast.skill_execute_failed},
     * which told neither the player nor the server owner what went wrong. The
     * error type now selects a dedicated key, the failing skill id is preserved,
     * and a configuration mistake is additionally logged once per occurrence
     * because it can only be fixed from the console side.
     */
    private CastAttemptResult describeCastFailure(SkillDefinition definition,
            String triggerId,
            CastOutcome outcome) {
        String skillId = definition == null ? "" : definition.id();
        String detail = outcome.failureDetail();
        CoreActionFailureKind kind = outcome.failureKind();
        FailureReason reason = failureReasonFor(kind, outcome.reasonKey());
        String messageKey = messageKeyFor(kind, outcome.reasonKey());
        if (isConfigurationError(kind)) {
            plugin.getLogger().warning("Skill '" + skillId + "' failed to cast via trigger '"
                    + Texts.toStringSafe(triggerId) + "': " + kind.name() + " - " + detail);
        }
        return CastAttemptResult.fail(
                reason,
                messageKey,
                Map.of(
                        "skill", definition == null || Texts.isBlank(definition.displayName())
                                ? skillId : definition.displayName(),
                        "skill_id", skillId,
                        "trigger_id", Texts.toStringSafe(triggerId),
                        "error_type", kind.name(),
                        "detail", detail,
                        "mythic_skill", Texts.toStringSafe(outcome.arg("mythic_skill"))),
                skillId,
                Texts.toStringSafe(triggerId));
    }

    /**
     * Classifies a pipeline failure for {@link CastAttemptResult}.
     *
     * <p>A missing Mythic skill is recognised by its reason key rather than its kind, because it shares
     * {@code INVALID_CONFIG} with every other configuration mistake while needing its own reason so the GUI can
     * name the skill that is absent.</p>
     *
     * @param kind the pipeline failure classification
     * @param reasonKey the failing stage's reason key
     * @return the cast failure reason
     */
    static FailureReason failureReasonFor(CoreActionFailureKind kind, String reasonKey) {
        if (MYTHIC_MISSING_REASON.equals(reasonKey)) {
            return FailureReason.MYTHIC_SKILL_NOT_FOUND;
        }
        return kind == CoreActionFailureKind.OWNER_DISABLED
                ? FailureReason.CANCELLED
                : FailureReason.MYTHIC_CAST_FAILED;
    }

    /**
     * Selects the player-facing message key for a pipeline failure.
     *
     * @param kind the pipeline failure classification
     * @param reasonKey the failing stage's reason key
     * @return one of the {@code cast.*} language keys
     */
    static String messageKeyFor(CoreActionFailureKind kind, String reasonKey) {
        if (MYTHIC_MISSING_REASON.equals(reasonKey)) {
            return "cast.mythic_not_found";
        }
        return switch (kind) {
            // MISSING_CONTEXT means a stage asked for something the phase does not provide, which reads the
            // same way to a server owner as the v1 "action not found": the line names something that is not
            // there.
            case MISSING_CONTEXT -> "cast.script_action_not_found";
            case INVALID_CONFIG -> "cast.script_invalid_argument";
            case TIMEOUT -> "cast.script_timeout";
            case OWNER_DISABLED -> "cast.cancelled";
            // WRONG_THREAD and REJECTED have no dedicated key; they are runtime conditions rather than
            // configuration mistakes, so they land on the generic message.
            case WRONG_THREAD, REJECTED, INTERNAL_ERROR -> "cast.skill_execute_failed";
        };
    }

    /**
     * Reports whether the failure is a server-side configuration mistake rather
     * than an expected in-game outcome. Only these are logged, so a normal failed
     * cast never floods the console.
     *
     * <p>{@code SYNTAX_ERROR} has no v2 counterpart at run time: a malformed line now fails to compile at load
     * time, where {@code SkillPipelineRuntime} already logs it.</p>
     */
    static boolean isConfigurationError(CoreActionFailureKind kind) {
        return kind == CoreActionFailureKind.INVALID_CONFIG
                || kind == CoreActionFailureKind.MISSING_CONTEXT
                || kind == CoreActionFailureKind.TIMEOUT;
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

    /**
     * Carries the pipeline outcome together with any scheduling failure, so the reason survives the hop back
     * onto the caster's thread.
     *
     * <p>{@code SKIPPED} and {@code PARTIAL} both count as success. "No enemy was in range" and "three of five
     * targets resisted" are gameplay results, not cast failures, and treating them as failures would consume no
     * resources and show an error message for a cast that visibly happened.</p>
     */
    private record CastOutcome(PipelineOutcome result, Throwable throwable) {

        private boolean success() {
            return throwable == null && result != null
                    && result.status() != PipelineOutcome.Status.FAILURE;
        }

        private CoreActionFailureKind failureKind() {
            if (throwable != null || result == null || result.failureKind() == null) {
                return CoreActionFailureKind.INTERNAL_ERROR;
            }
            return result.failureKind();
        }

        private String reasonKey() {
            return throwable != null || result == null ? "" : result.reasonKey();
        }

        private Object arg(String name) {
            return throwable != null || result == null ? "" : result.args().getOrDefault(name, "");
        }

        private String failureDetail() {
            if (throwable != null) {
                Throwable cause = AsyncFailures.unwrap(throwable);
                String message = cause == null ? null : cause.getMessage();
                return Texts.isBlank(message)
                        ? cause == null ? "unknown error" : cause.getClass().getSimpleName()
                        : message;
            }
            if (result == null) {
                return "no result";
            }
            return Texts.isBlank(result.reasonKey()) ? "no detail" : result.reasonKey();
        }
    }
}
