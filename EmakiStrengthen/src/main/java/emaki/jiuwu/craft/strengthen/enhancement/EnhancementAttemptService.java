package emaki.jiuwu.craft.strengthen.enhancement;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.math.CraftRollEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.craft.CraftOperationJournal;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.variable.VariableContext;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementOperationView;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementPityResult;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementPityTrack;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixLayerCodec;
import emaki.jiuwu.craft.strengthen.enhancement.cost.ConsumeTimingEnum;
import emaki.jiuwu.craft.strengthen.enhancement.cost.MaterialSlotConfig;
import emaki.jiuwu.craft.strengthen.enhancement.cost.TargetCompareEnum;
import emaki.jiuwu.craft.strengthen.enhancement.mastery.MasteryProgressService;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityDecayTypeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityIsolationEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityEffectTypeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityScopeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityState;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityStateStore;
import emaki.jiuwu.craft.strengthen.enhancement.progression.EnhancementProgressionResolver;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipe;
import emaki.jiuwu.craft.strengthen.enhancement.target.EnhancementTargetRegistry;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;

public final class EnhancementAttemptService {

    private static final int MAX_SAFE_LEVEL = 1_000_000;
    private static final String OPERATION_NAMESPACE = "strengthen_enhancement";
    private static final int MAX_JOURNAL_ENTRIES = 256;
    private static final String ERROR_COMPENSATION_PENDING = "strengthen.error.compensation_pending";
    private static final String ERROR_REBUILD_FAILED = "strengthen.error.rebuild_failed";
    private static final String ERROR_TARGET_CHANGED = "strengthen.enhancement.target_changed";
    private static final String ERROR_CONDITION_NOT_MET = "strengthen.error.condition_not_met";
    private static final String ERROR_CONTAINER_TARGET = "strengthen.enhancement.container_target_rejected";
    private static final String ERROR_CONTAINER_MATERIAL = "strengthen.enhancement.container_material_rejected";
    private static final String DEBUG_MODULE = "attempt";
    private static final NamespacedKey PITY_OWNER_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("emakistrengthen:pity_owner_id"));

    private final EmakiStrengthenPlugin plugin;
    private final EnhancementTargetRegistry targetRegistry;
    private final PityStateStore pityStateStore;
    private final EnhancementProgressionResolver progressionResolver;
    private final AffixLayerCodec affixLayerCodec;
    private final MasteryProgressService masteryProgressService;
    private final CraftOperationJournal<JournalEntry> operationJournal =
            CraftOperationJournal.ofMemory(MAX_JOURNAL_ENTRIES);
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public EnhancementAttemptService(EmakiStrengthenPlugin plugin,
            EnhancementTargetRegistry targetRegistry,
            PityStateStore pityStateStore,
            EnhancementProgressionResolver progressionResolver) {
        this(plugin, targetRegistry, pityStateStore, progressionResolver, null, null);
    }

    public EnhancementAttemptService(EmakiStrengthenPlugin plugin,
            EnhancementTargetRegistry targetRegistry,
            PityStateStore pityStateStore,
            EnhancementProgressionResolver progressionResolver,
            @Nullable AffixLayerCodec affixLayerCodec) {
        this(plugin, targetRegistry, pityStateStore, progressionResolver, affixLayerCodec, null);
    }

    public EnhancementAttemptService(EmakiStrengthenPlugin plugin,
            EnhancementTargetRegistry targetRegistry,
            PityStateStore pityStateStore,
            EnhancementProgressionResolver progressionResolver,
            @Nullable AffixLayerCodec affixLayerCodec,
            @Nullable MasteryProgressService masteryProgressService) {
        this.plugin = plugin;
        this.targetRegistry = targetRegistry;
        this.pityStateStore = pityStateStore;
        this.progressionResolver = Objects.requireNonNull(progressionResolver, "progressionResolver");
        this.affixLayerCodec = affixLayerCodec;
        this.masteryProgressService = masteryProgressService;
    }

    public boolean accepting() {
        return accepting.get();
    }

    public void freezeAccepting() {
        accepting.set(false);
    }

    public void resumeAccepting() {
        accepting.set(true);
    }

    public boolean drain(long timeout, TimeUnit unit) {
        return operationJournal.drain(timeout, unit);
    }

    public @NotNull Map<String, String> journalSnapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        operationJournal.snapshot().forEach((key, entry) -> snapshot.put(key, entry.phase()));
        return Map.copyOf(snapshot);
    }

    public @NotNull Map<String, String> journalPhases() {
        return journalSnapshot();
    }

    public @Nullable EnhancementOperationView operationView(@Nullable String operationId) {
        if (Texts.isBlank(operationId)) {
            return null;
        }
        for (Map.Entry<String, CraftOperationJournal.Entry<JournalEntry>> entry
                : operationJournal.snapshot().entrySet()) {
            if (operationId.equals(operationIdOf(entry.getKey()))) {
                return toOperationView(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    public @NotNull List<EnhancementOperationView> operationViews() {
        List<EnhancementOperationView> views = new ArrayList<>();
        operationJournal.snapshot().forEach((key, entry) -> {
            EnhancementOperationView view = toOperationView(key, entry);
            if (view != null) {
                views.add(view);
            }
        });
        return List.copyOf(views);
    }

    private @Nullable EnhancementOperationView toOperationView(String journalKey,
            CraftOperationJournal.Entry<JournalEntry> entry) {
        String operationId = operationIdOf(journalKey);
        if (Texts.isBlank(operationId)) {
            return null;
        }
        String phase = entry == null ? "" : Texts.toStringSafe(entry.phase());
        JournalEntry payload = entry == null ? null : entry.payload();
        EnhancementAttemptResult result = payload == null ? null : payload.result();
        boolean pending = ERROR_COMPENSATION_PENDING.equals(phase)
                || EnhancementOperationView.PHASE_COMPENSATION_PENDING.equals(phase)
                || (result != null && ERROR_COMPENSATION_PENDING.equals(result.errorKey()));
        return new EnhancementOperationView(operationId,
                entry == null ? null : entry.playerId(), phase, pending);
    }

    private static String operationIdOf(String journalKey) {
        if (Texts.isBlank(journalKey)) {
            return "";
        }
        int separator = journalKey.indexOf(':');
        return separator < 0 ? journalKey : journalKey.substring(separator + 1);
    }

    public @NotNull EnhancementAttemptResult attempt(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied) {
        return attempt(player, recipe, target, supplied, UUID.randomUUID().toString());
    }

    public @NotNull EnhancementAttemptResult attempt(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied,
            @Nullable String operationId) {
        return attempt(player, recipe, target, supplied, operationId, null);
    }

    public @NotNull EnhancementAttemptResult attempt(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied,
            @Nullable String operationId,
            @Nullable EnhancementTargetVariables.Snapshot expectedSnapshot) {
        return attempt(player, recipe, target, supplied, operationId, expectedSnapshot, null);
    }

    public @NotNull EnhancementAttemptResult attemptWithPreview(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied,
            @Nullable String operationId,
            @Nullable EnhancementPreviewSession previewSession) {
        return attempt(player, recipe, target, supplied, operationId,
                previewSession == null ? null : previewSession.snapshot(),
                previewSession == null ? null : previewSession.plan());
    }

    private @NotNull EnhancementAttemptResult attempt(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied,
            @Nullable String operationId,
            @Nullable EnhancementTargetVariables.Snapshot expectedSnapshot,
            @Nullable ExecutionPlan preparedPlan) {
        String operation = Texts.isBlank(operationId) ? UUID.randomUUID().toString() : operationId;
        String journalKey = journalKey(player, operation);
        int fingerprint = attemptFingerprint(recipe, target, supplied);
        AttemptStart start = beginOperation(journalKey, player == null ? null : player.getUniqueId(), fingerprint);
        if (start.existingResult() != null) {
            return start.existingResult();
        }
        if (!start.started()) {
            return rejectedWithOperation(start.errorKey(), operation);
        }
        EnhancementAttemptResult result = null;
        try {
            result = attemptOnce(player, recipe, target, supplied, operation, expectedSnapshot, preparedPlan);
            return result;
        } catch (RuntimeException | LinkageError exception) {
            warn("强化框架执行失败", exception);
            result = rejectedWithOperation("strengthen.error.internal", operation);
            return result;
        } finally {
            if (result == null) {
                result = rejectedWithOperation("strengthen.error.internal", operation);
            }
            completeOperation(journalKey, fingerprint, result);
            finishInFlight(journalKey);
        }
    }

    private @NotNull EnhancementAttemptResult attemptOnce(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied,
            @NotNull String operation,
            @Nullable EnhancementTargetVariables.Snapshot expectedSnapshot,
            @Nullable ExecutionPlan preparedPlan) {
        if (player == null || !player.isOnline()) {
            return EnhancementAttemptResult.rejected("strengthen.enhancement.no_player");
        }
        if (recipe == null) {
            return EnhancementAttemptResult.rejected("strengthen.error.no_recipe");
        }
        if (target == null || target.getType().isAir()) {
            return EnhancementAttemptResult.rejected("strengthen.error.no_target");
        }
        EnhancementTargetProvider provider = resolveProvider(player, recipe, target);
        if (provider == null) {
            return EnhancementAttemptResult.rejected("strengthen.enhancement.provider_not_found",
                    Map.of("provider", recipe.target().provider()));
        }
        if (containerBackedTarget(player, target)) {
            logContainerRejection(player, target);
            return rejectedWithOperation(ERROR_CONTAINER_TARGET, operation);
        }
        if (requiresItemPityOwner(recipe) && !ensurePityOwner(target)) {
            return rejectedWithOperation(ERROR_REBUILD_FAILED, operation);
        }
        List<ItemStack> suppliedItems = supplied == null ? List.of() : supplied;
        if (containerBackedMaterials(player, suppliedItems)) {
            logContainerMaterialRejection(player, suppliedItems.size());
            return rejectedWithOperation(ERROR_CONTAINER_MATERIAL, operation);
        }
        ExecutionPlan plan = reusablePlan(player, target, provider, preparedPlan);
        if (plan == null) {
            plan = prepare(player, recipe, target, suppliedItems, provider);
        }
        if (!plan.valid()) {
            return rejectedPlan(plan, operation);
        }
        if (!sameFrozenTarget(expectedSnapshot, plan.snapshot(), player, operation)) {
            return rejectedWithOperation(ERROR_TARGET_CHANGED, operation);
        }

        boolean success = plan.forceSuccess() || CraftRollEngine.roll(plan.effectiveRate());
        StrengthenEconomyService economy = plugin == null ? null : plugin.economyService();
        StrengthenEconomyService.ChargeResult charge = chargeCurrencies(player, plan.costs(), economy, operation);
        if (!charge.success()) {
            if (charge.compensationPending()) {
                return rejectedWithOperation("strengthen.error.compensation_pending", operation);
            }
            return rejectedWithOperation(charge.errorKey().isBlank()
                    ? "strengthen.error.insufficient_funds" : charge.errorKey(), operation);
        }

        Map<ItemStack, Integer> materialAmounts = snapshotAmounts(plan.materialMatches());
        ItemStack originalTarget = target.clone();
        ItemStack commitCandidate = success ? plan.preparedTarget() : plan.failureTarget();
        int commitLevel = success ? plan.targetLevel() : plan.failureLevel();
        try {
            consumeMaterials(plan.materialMatches(), success);
            if (commitCandidate != null && !commitPreparedTarget(player, target, commitCandidate, originalTarget,
                    provider, commitLevel)) {
                restoreAmounts(materialAmounts);
                return compensateAfterCommitFailure(player, economy, charge.appliedCosts(), operation,
                        "strengthen.error.rebuild_failed");
            }
        } catch (RuntimeException | LinkageError exception) {
            restoreAmounts(materialAmounts);
            warn("强化框架材料/目标提交失败", exception);
            return compensateAfterCommitFailure(player, economy, charge.appliedCosts(), operation,
                    "strengthen.error.rebuild_failed");
        }

        if (masteryProgressService != null) {
            masteryProgressService.recordAttempt(target, success);
        }

        List<PityView> updated;
        try {
            updated = updatePityTracks(plan.pityTracks(), success);
        } catch (RuntimeException | LinkageError exception) {
            warn("强化框架保底状态写回失败", exception);
            updated = plan.pityTracks();
        }
        int resultingLevel = success ? plan.targetLevel()
                : (commitCandidate == null ? plan.currentLevel() : plan.failureLevel());
        Map<String, String> resultPlaceholders = Map.of("operation_id", operation);
        EnhancementPityResult pityResult = toPityResult(updated);
        EnhancementAttemptResult result = new EnhancementAttemptResult(true, success, "", resultPlaceholders,
                plan.currentLevel(), resultingLevel, plan.effectiveRate(), pityResult);
        if (plugin.actionCoordinator() != null) {
            plugin.actionCoordinator().triggerEnhancementActions(player, recipe, target, result, operation);
        }
        return result;
    }

    public @NotNull EnhancementAttemptPreview preview(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied) {
        return previewSession(player, recipe, target, supplied).preview();
    }

    public @NotNull EnhancementPreviewSession previewSession(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied) {
        if (player == null || !player.isOnline()) {
            return EnhancementPreviewSession.rejected("strengthen.enhancement.no_player");
        }
        if (recipe == null) {
            return EnhancementPreviewSession.rejected("strengthen.error.no_recipe");
        }
        if (target == null || target.getType().isAir()) {
            return EnhancementPreviewSession.rejected("strengthen.error.no_target");
        }
        EnhancementTargetProvider provider = resolveProvider(player, recipe, target);
        if (provider == null) {
            return EnhancementPreviewSession.rejected("strengthen.enhancement.provider_not_found");
        }
        ExecutionPlan plan = prepare(player, recipe, target, supplied == null ? List.of() : supplied, provider);
        return new EnhancementPreviewSession(plan.preview(), plan.snapshot(), plan);
    }

    private @Nullable ExecutionPlan reusablePlan(Player player,
            ItemStack target,
            EnhancementTargetProvider provider,
            @Nullable ExecutionPlan candidate) {
        if (candidate == null || !candidate.valid()) {
            return null;
        }
        EnhancementTargetVariables.Snapshot current =
                EnhancementTargetVariables.capture(player, target, provider);
        EnhancementTargetVariables.Snapshot frozen = candidate.snapshot();
        if (frozen == null || !frozen.sameIdentityAndVersion(current)
                || frozen.level() != current.level() || frozen.temper() != current.temper()) {
            debug(player, "debug.attempt.plan_recomputed", Map.of(
                    "recipe", candidate.recipe() == null ? "-" : candidate.recipe().id(),
                    "reason", "target_state_changed"));
            return null;
        }
        for (MaterialMatch match : candidate.materialMatches()) {
            if (match.stack() == null || match.stack().getAmount() < match.amount()) {
                debug(player, "debug.attempt.plan_recomputed", Map.of(
                        "recipe", candidate.recipe() == null ? "-" : candidate.recipe().id(),
                        "reason", "material_amount_changed"));
                return null;
            }
        }
        return candidate;
    }

    private @Nullable EnhancementTargetProvider resolveProvider(@Nullable Player player,
            EnhancementRecipe recipe,
            ItemStack target) {
        if (targetRegistry == null) {
            return null;
        }
        String providerId = recipe.target().provider();
        EnhancementTargetProvider provider = Texts.isBlank(providerId) ? null : targetRegistry.get(providerId);
        if (provider == null) {
            return null;
        }
        try {
            return provider.canHandle(player, target) ? provider : null;
        } catch (RuntimeException | LinkageError exception) {
            warn("目标 Provider '" + providerId + "' 的 canHandle 抛出异常", exception);
            return null;
        }
    }

    private ExecutionPlan prepare(Player player,
            EnhancementRecipe recipe,
            ItemStack target,
            List<ItemStack> supplied,
            EnhancementTargetProvider provider) {
        EnhancementTargetVariables.Snapshot snapshot = EnhancementTargetVariables.capture(player, target, provider);
        reportUnreadablePdc(player, snapshot);
        int currentLevel = snapshot.level();
        int currentTemper = snapshot.temper();
        if (currentLevel >= MAX_SAFE_LEVEL) {
            return ExecutionPlan.invalid(recipe, ERROR_REBUILD_FAILED, snapshot);
        }

        VariableContext targetVariables = buildVariablesFromSnapshot(player, snapshot, currentLevel);
        if (!matchesTargetFilter(recipe, target, targetVariables)) {
            return ExecutionPlan.invalid(recipe, ERROR_REBUILD_FAILED, snapshot);
        }
        if (!satisfiesRecipeConditions(recipe, player, target, targetVariables)) {
            return ExecutionPlan.invalid(recipe, ERROR_CONDITION_NOT_MET, snapshot);
        }

        EnhancementProgressionResolver.Resolution progression;
        try {
            progression = progressionResolver.resolve(recipe, currentLevel,
                    evaluationLevel -> buildVariablesFromSnapshot(player, snapshot, evaluationLevel));
        } catch (RuntimeException | LinkageError exception) {
            warn("强化进度解析失败", exception);
            return ExecutionPlan.invalid(recipe, "strengthen.error.rebuild_failed", snapshot);
        }
        int targetLevel = progression.levels().targetLevel();
        VariableContext variables = progression.variables();
        List<Integer> materialAmounts = progression.currentMaterialAmounts();
        List<MaterialMatch> materialMatches = matchMaterials(
                recipe, player, target, supplied, variables, materialAmounts, targetLevel, provider);
        List<AttemptCost> costs = applyCostGrowth(progression.currentCosts(), currentLevel);
        List<PityView> pityTracks = loadPityTracks(recipe, player, target, provider, variables);
        double baseRate = clampChance(progression.chance().current());
        double effectiveRate = applyDiminishingReturns(baseRate, currentLevel);
        boolean forceSuccess = false;
        for (PityView track : pityTracks) {
            if (!track.triggered() || track.config() == null) {
                continue;
            }
            PityEffectTypeEnum effectType = track.config().effect().type();
            if (effectType == PityEffectTypeEnum.FORCE_SUCCESS) {
                forceSuccess = true;
                effectiveRate = 1D;
            } else if (effectType == PityEffectTypeEnum.CHANCE_BONUS) {
                Double bonus = track.config().effect().bonusValue();
                effectiveRate = clampChance(effectiveRate + (bonus == null ? 0D : bonus));
            }
        }
        if (!forceSuccess) {
            effectiveRate = applyMinimumSuccessRate(effectiveRate);
        }
        PityView primaryPity = pityTracks.isEmpty()
                ? new PityView(null, null, null, 0, false)
                : pityTracks.get(0);
        boolean anyTriggered = false;
        for (PityView track : pityTracks) {
            anyTriggered = anyTriggered || track.triggered();
        }

        List<EnhancementAttemptPreview.MaterialRequirement> requirements = materialRequirements(
                recipe, player, target, supplied, variables, materialAmounts, targetLevel, provider);
        if (materialMatches == null) {
            EnhancementAttemptPreview preview = EnhancementAttemptPreview.invalid(
                    "strengthen.error.material_missing", currentLevel, targetLevel,
                    baseRate, effectiveRate, primaryPity.counter(), anyTriggered, costs, requirements);
            return new ExecutionPlan(false, recipe, currentLevel, targetLevel, currentTemper, null, pityTracks,
                    baseRate, effectiveRate, forceSuccess, costs, List.of(), preview, snapshot, null, currentLevel);
        }

        if (targetLevel <= currentLevel || targetLevel > MAX_SAFE_LEVEL) {
            EnhancementAttemptPreview preview = EnhancementAttemptPreview.invalid(
                    "strengthen.error.rebuild_failed", currentLevel, targetLevel,
                    baseRate, effectiveRate, primaryPity.counter(), anyTriggered, costs, requirements);
            return new ExecutionPlan(false, recipe, currentLevel, targetLevel, currentTemper, null, pityTracks,
                    baseRate, effectiveRate, forceSuccess, costs, materialMatches, preview, snapshot,
                    null, currentLevel);
        }

        PreparedLevel prepared = prepareLevelWriteBack(player, target, provider, targetLevel);
        if (!prepared.usable()) {
            EnhancementAttemptPreview preview = EnhancementAttemptPreview.invalid(
                    prepared.errorKey(), currentLevel, targetLevel,
                    baseRate, effectiveRate, primaryPity.counter(), anyTriggered, costs, requirements);
            return new ExecutionPlan(false, recipe, currentLevel, targetLevel, currentTemper, null, pityTracks,
                    baseRate, effectiveRate, forceSuccess, costs, materialMatches, preview, snapshot,
                    null, currentLevel);
        }

        int failureLevel = resolveFailureLevel(currentLevel);
        ItemStack failureTarget = null;
        if (failureLevel != currentLevel) {
            PreparedLevel demoted = prepareLevelWriteBack(player, target, provider, failureLevel);
            if (!demoted.usable()) {
                EnhancementAttemptPreview preview = EnhancementAttemptPreview.invalid(
                        demoted.errorKey(), currentLevel, targetLevel,
                        baseRate, effectiveRate, primaryPity.counter(), anyTriggered, costs, requirements);
                return new ExecutionPlan(false, recipe, currentLevel, targetLevel, currentTemper, null, pityTracks,
                        baseRate, effectiveRate, forceSuccess, costs, materialMatches, preview, snapshot,
                        null, currentLevel);
            }
            failureTarget = demoted.itemStack();
        }

        EnhancementAttemptPreview preview = EnhancementAttemptPreview.valid(
                recipe.id(), currentLevel, targetLevel, baseRate, effectiveRate,
                primaryPity.counter(), anyTriggered, costs, requirements);
        return new ExecutionPlan(true, recipe, currentLevel, targetLevel, currentTemper, prepared.itemStack(),
                pityTracks, baseRate, effectiveRate, forceSuccess, costs, materialMatches, preview, snapshot,
                failureTarget, failureLevel);
    }

    private PreparedLevel prepareLevelWriteBack(Player player,
            ItemStack target,
            EnhancementTargetProvider provider,
            int level) {
        try {
            ItemStack candidate = target.clone();
            provider.writeLevel(player, candidate, level);
            if (provider.readLevel(player, candidate) != level) {
                return PreparedLevel.failed(ERROR_REBUILD_FAILED);
            }
            String refreshError = refreshPreparedPresentation(player, candidate, provider);
            if (Texts.isNotBlank(refreshError)) {
                return PreparedLevel.failed(refreshError);
            }
            if (provider.readLevel(player, candidate) != level) {
                warn("刷新阶段改变了目标等级，已拒绝提交", null);
                return PreparedLevel.failed(ERROR_REBUILD_FAILED);
            }
            return new PreparedLevel(candidate, "");
        } catch (RuntimeException | LinkageError exception) {
            warn("目标 Provider 预写回失败", exception);
            return PreparedLevel.failed(ERROR_REBUILD_FAILED);
        }
    }

    private String journalKey(@Nullable Player player, String operationId) {
        return (player == null ? "-" : player.getUniqueId().toString()) + ":" + operationId;
    }


    private int attemptFingerprint(@Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied) {
        return Objects.hash(recipe == null ? "" : recipe.id(), target,
                supplied == null ? List.of() : supplied);
    }


    private AttemptStart beginOperation(String journalKey, @Nullable UUID playerId, int fingerprint) {
        CraftOperationJournal.Entry<JournalEntry> existing = operationJournal.beginIfAbsent(
                journalKey, OPERATION_NAMESPACE, playerId, new JournalEntry(fingerprint, null));
        if (existing != null) {
            JournalEntry payload = existing.payload();
            if (payload == null || payload.fingerprint() != fingerprint) {
                return new AttemptStart(false, null, "strengthen.error.operation_conflict");
            }
            return payload.result() == null
                    ? new AttemptStart(false, null, "strengthen.error.operation_in_progress")
                    : new AttemptStart(false, payload.result(), "");
        }
        if (!accepting.get()) {
            operationJournal.archive(journalKey);
            return new AttemptStart(false, null, "strengthen.error.not_accepting");
        }
        pruneJournal();
        return new AttemptStart(true, null, "");
    }

    private void completeOperation(String journalKey, int fingerprint, EnhancementAttemptResult result) {
        String phase = result.committed()
                ? (result.success() ? "COMMITTED_SUCCESS" : "COMMITTED_FAILURE")
                : ("strengthen.error.compensation_pending".equals(result.errorKey())
                        ? "COMPENSATION_PENDING" : "NOT_COMMITTED");
        operationJournal.update(journalKey, phase, new JournalEntry(fingerprint, result));
        pruneJournal();
    }

    private void finishInFlight(String journalKey) {
        operationJournal.release(journalKey);
    }


    private void pruneJournal() {
        operationJournal.prune(entry -> {
            EnhancementAttemptResult result = entry.payload() == null ? null : entry.payload().result();
            return result != null && !"strengthen.error.compensation_pending".equals(result.errorKey());
        });
    }

    private EnhancementAttemptResult rejectedPlan(ExecutionPlan plan, String operation) {
        String errorKey = plan.preview() == null || plan.preview().errorKey().isBlank()
                ? "strengthen.error.rebuild_failed" : plan.preview().errorKey();
        return rejectedWithOperation(errorKey, operation);
    }

    private EnhancementAttemptResult rejectedWithOperation(String errorKey, String operation) {
        Map<String, String> placeholders = Texts.isBlank(operation)
                ? Map.of()
                : Map.of("operation_id", operation);
        return EnhancementAttemptResult.rejected(errorKey, placeholders);
    }

    private EnhancementAttemptResult compensateAfterCommitFailure(Player player,
            StrengthenEconomyService economy,
            List<AttemptCost> appliedCosts,
            String operation,
            String fallbackError) {
        if (economy == null) {
            return rejectedWithOperation("strengthen.error.compensation_pending", operation);
        }
        StrengthenEconomyService.RefundResult refund = economy.refundWithResult(player, appliedCosts, operation);
        if (!refund.success()) {
            return rejectedWithOperation("strengthen.error.compensation_pending", operation);
        }
        return rejectedWithOperation(fallbackError, operation);
    }

    private void warn(String message, Throwable throwable) {
        if (plugin != null && plugin.getLogger() != null) {
            plugin.getLogger().warning(message + ": "
                    + (throwable == null ? "unknown" : String.valueOf(throwable.getMessage())));
        }
    }

    private void debug(@Nullable Player player, String langKey, Map<String, ?> replacements) {
        if (plugin == null || plugin.debugLogger() == null) {
            return;
        }
        plugin.debugLogger().log(DEBUG_MODULE, player, langKey, replacements);
    }

    private @NotNull String refreshPreparedPresentation(Player player,
            ItemStack preparedTarget,
            EnhancementTargetProvider provider) {
        EmakiResult<Unit> refresh;
        try {
            refresh = provider.refreshPresentation(player, preparedTarget);
        } catch (RuntimeException | LinkageError exception) {
            warn("目标 Provider 刷新阶段抛出异常", exception);
            debug(player, "debug.attempt.refresh_failed", Map.of(
                    "provider", safeProviderId(provider),
                    "reason", exception.getClass().getSimpleName(),
                    "error_key", ERROR_REBUILD_FAILED));
            return ERROR_REBUILD_FAILED;
        }
        if (refresh == null) {
            return ERROR_REBUILD_FAILED;
        }
        if (refresh.isSuccess()) {
            return "";
        }
        String reasonKey = Texts.isBlank(refresh.reasonKey()) ? ERROR_REBUILD_FAILED : refresh.reasonKey();
        warn("目标 Provider 刷新阶段失败，未扣费即拒绝 | 原因键=" + reasonKey, null);
        debug(player, "debug.attempt.refresh_failed", Map.of(
                "provider", safeProviderId(provider),
                "reason", refresh.failureKind() == null ? "unknown" : refresh.failureKind().name(),
                "error_key", reasonKey));
        return reasonKey;
    }

    private boolean satisfiesRecipeConditions(EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            VariableContext variables) {
        if (!recipe.conditionsConfigured()) {
            return true;
        }
        Map<String, Object> values = variables == null ? Map.of() : variables.toMap();
        try {
            boolean passed = ConditionEvaluator.evaluate(recipe.conditions(),
                    text -> PlaceholderRenderer.renderPapi(player, text, null, "strengthen_enhancement"),
                    ConditionContext.of(player, target, values));
            if (!passed) {
                debug(player, "debug.attempt.condition_rejected", Map.of(
                        "recipe", recipe.id(),
                        "condition_type", recipe.conditions().conditionType(),
                        "required", recipe.conditions().requiredCount()));
            }
            return passed;
        } catch (RuntimeException | LinkageError exception) {
            warn("配方条件求值失败，按不通过处理", exception);
            debug(player, "debug.attempt.condition_rejected", Map.of(
                    "recipe", recipe.id(),
                    "condition_type", exception.getClass().getSimpleName(),
                    "required", 0));
            return false;
        }
    }

    private boolean sameFrozenTarget(@Nullable EnhancementTargetVariables.Snapshot expected,
            @Nullable EnhancementTargetVariables.Snapshot actual,
            Player player,
            String operation) {
        if (expected == null || actual == null) {
            return true;
        }
        if (expected.sameIdentityAndVersion(actual)) {
            return true;
        }
        warn("目标在预览与确认之间发生变化，已在扣费前拒绝 | 操作=" + operation, null);
        debug(player, "debug.attempt.target_changed", Map.of(
                "operation_id", operation,
                "expected_version", expected.version(),
                "actual_version", actual.version(),
                "expected_instance", expected.instanceId(),
                "actual_instance", actual.instanceId()));
        return false;
    }

    private boolean containerBackedTarget(Player player, ItemStack target) {
        if (plugin == null || plugin.appConfig() == null
                || !plugin.appConfig().enhancementRejectContainerTarget()) {
            return false;
        }
        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();
        if (top.getHolder() instanceof Player) {
            return false;
        }
        for (ItemStack slot : top.getContents()) {
            if (slot == target) {
                return true;
            }
        }
        return false;
    }

    private void logContainerRejection(Player player, ItemStack target) {
        warn("强化目标位于外部容器，已拒绝执行", null);
        debug(player, "debug.attempt.container_rejected", Map.of(
                "item", target == null ? "-" : target.getType().name(),
                "error_key", ERROR_CONTAINER_TARGET));
    }

    private void reportUnreadablePdc(Player player, EnhancementTargetVariables.Snapshot snapshot) {
        if (snapshot.unreadablePdcCount() <= 0) {
            return;
        }
        debug(player, "debug.attempt.pdc_unreadable", Map.of(
                "count", snapshot.unreadablePdcCount(),
                "keys", String.join(",", snapshot.unreadablePdcKeys()),
                "provider", snapshot.providerId()));
    }

    private static String safeProviderId(EnhancementTargetProvider provider) {
        try {
            return Texts.toStringSafe(provider.id());
        } catch (RuntimeException | LinkageError exception) {
            return "";
        }
    }

    private VariableContext buildVariablesFromSnapshot(Player player,
            EnhancementTargetVariables.Snapshot snapshot,
            int evaluationLevel) {
        int currentLevel = snapshot.level();
        int temper = snapshot.temper();
        int targetLevel = currentLevel == Integer.MAX_VALUE ? Integer.MAX_VALUE : currentLevel + 1;
        int previousLevel = Math.max(0, currentLevel - 1);
        VariableContext.Builder builder = VariableContext.builder(player)
                .with("target.level", evaluationLevel)
                .with("target.temper", temper)
                .with("target_level", evaluationLevel)
                .with("target_temper", temper)
                .with("current_level", currentLevel)
                .with("previous_level", previousLevel)
                .with("resulting_level", targetLevel)
                .with("next_level", targetLevel)
                .with("target.current_level", currentLevel)
                .with("target.previous_level", previousLevel)
                .with("target.resulting_level", targetLevel);
        snapshot.enrichVariables(builder);
        return builder.build();
    }


    private @Nullable List<MaterialMatch> matchMaterials(EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            List<ItemStack> supplied,
            VariableContext variables,
            List<Integer> requiredAmounts,
            int targetLevel,
            EnhancementTargetProvider provider) {
        if (recipe.materials().isEmpty()) {
            return List.of();
        }
        List<MaterialMatch> matches = new ArrayList<>();
        Map<ItemStack, Integer> consumedPerStack = new IdentityHashMap<>();
        ItemSourceRef targetSource = identifySource(target);
        Map<ItemStack, VariableContext> comparisonVariables = new IdentityHashMap<>();
        for (int slotIndex = 0; slotIndex < recipe.materials().size(); slotIndex++) {
            MaterialSlotConfig slot = recipe.materials().get(slotIndex);
            int required = slotIndex < requiredAmounts.size()
                    ? Math.max(0, requiredAmounts.get(slotIndex))
                    : 0;
            if (required == 0) {
                continue;
            }
            int remaining = required;
            List<MaterialMatch> slotMatches = new ArrayList<>();
            for (ItemStack candidate : supplied) {
                if (candidate == null || candidate.getType().isAir() || remaining <= 0) {
                    continue;
                }
                int alreadyUsed = consumedPerStack.getOrDefault(candidate, 0);
                int available = candidate.getAmount() - alreadyUsed;
                if (available <= 0 || !testMatcher(slot, candidate, target, targetSource, player, variables,
                        targetLevel, provider, comparisonVariables)) {
                    continue;
                }
                int take = Math.min(available, remaining);
                slotMatches.add(new MaterialMatch(candidate, take, slot.consumeTiming()));
                consumedPerStack.merge(candidate, take, Integer::sum);
                remaining -= take;
            }
            if (remaining > 0 && slot.required()) {
                return null;
            }
            matches.addAll(slotMatches);
        }
        return List.copyOf(matches);
    }

    private boolean testMatcher(MaterialSlotConfig slot,
            ItemStack candidate,
            ItemStack target,
            @Nullable ItemSourceRef targetSource,
            Player player,
            VariableContext variables,
            int targetLevel,
            EnhancementTargetProvider provider,
            Map<ItemStack, VariableContext> comparisonVariables) {
        try {
            VariableContext effective = variables;
            if (slot.comparesTarget()) {
                effective = comparisonVariables.computeIfAbsent(candidate,
                        stack -> EnhancementMaterialVariables.enrich(variables, stack, target, targetLevel,
                                provider, affixLayerCodec));
                if (!satisfiesTargetCompare(slot.targetCompare(), effective)) {
                    return false;
                }
            }
            MatchContext context = new MatchContext(candidate, identifySource(candidate), player,
                    target, targetSource, effective);
            return slot.matcher().test(context);
        } catch (RuntimeException | LinkageError exception) {
            warn("材料 Matcher 判定抛出异常，视为不匹配", exception);
            return false;
        }
    }

    private static boolean satisfiesTargetCompare(TargetCompareEnum compare, VariableContext variables) {
        Map<String, Object> values = variables.toMap();
        return switch (compare) {
            case NONE -> true;
            case SAME_AFFIX -> flag(values, EnhancementMaterialVariables.VARIABLE_SHARED_AFFIX_COUNT) > 0;
            case SAME_AFFIX_SET -> flag(values, EnhancementMaterialVariables.VARIABLE_SAME_AFFIX_SET) == 1;
            case SAME_ITEM_TYPE -> flag(values, EnhancementMaterialVariables.VARIABLE_SAME_ITEM_TYPE) == 1;
            case SAME_LEVEL -> flag(values, EnhancementMaterialVariables.VARIABLE_SAME_LEVEL) == 1;
            case LEVEL_AT_LEAST -> flag(values, EnhancementMaterialVariables.VARIABLE_LEVEL_AT_LEAST_TARGET) == 1;
        };
    }

    private static int flag(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private boolean matchesTargetFilter(EnhancementRecipe recipe,
            ItemStack target,
            VariableContext variables) {
        Map<String, Object> filter = recipe.target().filter();
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        Map<String, Object> values = variables == null ? Map.of() : variables.toMap();
        ItemSourceRef source = identifySource(target);
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String key = Texts.lower(entry.getKey());
            Object expected = entry.getValue();
            Object actual;
            if ("item_type".equals(key) || "material".equals(key)
                    || "target.item_type".equals(key) || "target.material".equals(key)) {
                actual = values.get("target_item_type");
            } else if ("item_source".equals(key) || "target.item_source".equals(key)) {
                actual = source == null ? "" : source.toString();
            } else if (key.startsWith("variable.")) {
                actual = values.get(key.substring("variable.".length()));
            } else if (values.containsKey(key)) {
                actual = values.get(key);
            } else {
                return false;
            }
            if (!sameFilterValue(actual, expected)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameFilterValue(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
            return Double.compare(actualNumber.doubleValue(), expectedNumber.doubleValue()) == 0;
        }
        return String.valueOf(actual).equalsIgnoreCase(String.valueOf(expected));
    }

    private boolean ensurePityOwner(ItemStack target) {
        if (target == null || target.getType().isAir()) {
            return false;
        }
        ItemMeta meta = target.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (!meta.getPersistentDataContainer().has(PITY_OWNER_KEY, PersistentDataType.STRING)) {
            meta.getPersistentDataContainer().set(PITY_OWNER_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            if (!target.setItemMeta(meta)) {
                return false;
            }
        }
        return target.getItemMeta() != null
                && target.getItemMeta().getPersistentDataContainer().has(PITY_OWNER_KEY, PersistentDataType.STRING);
    }

    private static int saturatedAdd(int left, int right) {
        long value = (long) Math.max(0, left) + Math.max(0, right);
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private @Nullable ItemSourceRef identifySource(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || plugin == null) {
            return null;
        }
        try {
            ItemSourceService itemSourceService = plugin.coreItemSourceService();
            return itemSourceService == null ? null : itemSourceService.identifyItem(itemStack);
        } catch (RuntimeException | LinkageError exception) {
            warn("识别材料物品源失败", exception);
            return null;
        }
    }

    private List<EnhancementAttemptPreview.MaterialRequirement> materialRequirements(
            EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            List<ItemStack> supplied,
            VariableContext variables,
            List<Integer> requiredAmounts,
            int targetLevel,
            EnhancementTargetProvider provider) {
        List<EnhancementAttemptPreview.MaterialRequirement> result = new ArrayList<>();
        ItemSourceRef targetSource = identifySource(target);
        Map<ItemStack, VariableContext> comparisonVariables = new IdentityHashMap<>();
        for (int index = 0; index < recipe.materials().size(); index++) {
            MaterialSlotConfig slot = recipe.materials().get(index);
            int required = index < requiredAmounts.size()
                    ? Math.max(0, requiredAmounts.get(index))
                    : 0;
            int available = 0;
            if (supplied != null) {
                for (ItemStack candidate : supplied) {
                    if (candidate != null && !candidate.getType().isAir()
                            && testMatcher(slot, candidate, target, targetSource, player, variables,
                                    targetLevel, provider, comparisonVariables)) {
                        available = saturatedAdd(available, candidate.getAmount());
                    }
                }
            }
            result.add(new EnhancementAttemptPreview.MaterialRequirement(
                    "material_" + (index + 1), required, available, slot.consumeTiming()));
        }
        return List.copyOf(result);
    }

    private boolean containerBackedMaterials(Player player, List<ItemStack> supplied) {
        if (supplied.isEmpty() || plugin == null || plugin.appConfig() == null
                || !plugin.appConfig().enhancementRejectContainerMaterial()) {
            return false;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof Player) {
            return false;
        }
        for (ItemStack slot : top.getContents()) {
            if (slot == null) {
                continue;
            }
            for (ItemStack candidate : supplied) {
                if (slot == candidate) {
                    return true;
                }
            }
        }
        return false;
    }

    private void logContainerMaterialRejection(Player player, int suppliedCount) {
        warn("强化材料位于外部容器，已拒绝执行", null);
        debug(player, "debug.attempt.container_material_rejected", Map.of(
                "count", suppliedCount,
                "error_key", ERROR_CONTAINER_MATERIAL));
    }

    private List<AttemptCost> applyCostGrowth(List<AttemptCost> costs, int currentLevel) {
        double perLevel = plugin == null || plugin.appConfig() == null
                ? 0D : plugin.appConfig().enhancementCostGrowthPerLevel();
        if (costs.isEmpty() || perLevel <= 0D || currentLevel <= 0) {
            return costs;
        }
        double multiplier = 1D + perLevel * currentLevel;
        double cap = plugin.appConfig().enhancementCostGrowthMaxMultiplier();
        if (cap > 0D) {
            multiplier = Math.min(multiplier, cap);
        }
        if (!Double.isFinite(multiplier) || multiplier <= 1D) {
            return costs;
        }
        List<AttemptCost> scaled = new ArrayList<>(costs.size());
        for (AttemptCost cost : costs) {
            double raw = cost.amount() * multiplier;
            long amount = raw >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, (long) Math.ceil(raw));
            scaled.add(new AttemptCost(cost.provider(), cost.currencyId(), cost.displayName(), amount));
        }
        return List.copyOf(scaled);
    }

    private double applyDiminishingReturns(double baseRate, int currentLevel) {
        double perLevel = plugin == null || plugin.appConfig() == null
                ? 0D : plugin.appConfig().enhancementDiminishingPerLevel();
        if (perLevel <= 0D || currentLevel <= 0) {
            return baseRate;
        }
        int startLevel = plugin.appConfig().enhancementDiminishingStartLevel();
        if (currentLevel < startLevel) {
            return baseRate;
        }
        double factor = 1D - perLevel * (currentLevel - startLevel + 1);
        return clampChance(baseRate * Math.max(0D, factor));
    }

    private double applyMinimumSuccessRate(double rate) {
        double floor = plugin == null || plugin.appConfig() == null
                ? 0D : plugin.appConfig().enhancementMinSuccessRate();
        if (floor <= 0D) {
            return rate;
        }
        return clampChance(Math.max(rate, floor));
    }

    private int resolveFailureLevel(int currentLevel) {
        int demotion = plugin == null || plugin.appConfig() == null
                ? 0 : plugin.appConfig().enhancementFailureDemotionLevels();
        if (demotion <= 0 || currentLevel <= 0) {
            return currentLevel;
        }
        int floor = Math.max(0, plugin.appConfig().enhancementFailureDemotionFloor());
        if (currentLevel <= floor) {
            return currentLevel;
        }
        return Math.max(floor, currentLevel - demotion);
    }

    private StrengthenEconomyService.ChargeResult chargeCurrencies(Player player,
            List<AttemptCost> costs,
            StrengthenEconomyService economy,
            String operation) {
        if (costs == null || costs.isEmpty()) {
            return StrengthenEconomyService.ChargeResult.success(List.of());
        }
        if (economy == null) {
            return StrengthenEconomyService.ChargeResult.failure(
                    "strengthen.error.economy_provider_unavailable", List.of());
        }
        try {
            return economy.charge(player, costs, operation);
        } catch (RuntimeException | LinkageError exception) {
            warn("强化框架扣费失败", exception);
            return StrengthenEconomyService.ChargeResult.failure(
                    "strengthen.error.economy_provider_unavailable", List.of());
        }
    }

    private Map<ItemStack, Integer> snapshotAmounts(List<MaterialMatch> matches) {
        Map<ItemStack, Integer> snapshot = new IdentityHashMap<>();
        if (matches != null) {
            for (MaterialMatch match : matches) {
                if (match != null && match.stack() != null) {
                    snapshot.putIfAbsent(match.stack(), match.stack().getAmount());
                }
            }
        }
        return snapshot;
    }

    private void restoreAmounts(Map<ItemStack, Integer> snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshot.forEach((stack, amount) -> {
            if (stack != null) {
                stack.setAmount(Math.max(0, amount));
            }
        });
    }

    private void consumeMaterials(List<MaterialMatch> matches, boolean success) {
        for (MaterialMatch match : matches) {
            if (!shouldConsume(match.timing(), success)) {
                continue;
            }
            ItemStack stack = match.stack();
            int remaining = stack.getAmount() - match.amount();
            stack.setAmount(Math.max(0, remaining));
        }
    }

    private static boolean shouldConsume(ConsumeTimingEnum timing, boolean success) {
        return switch (timing) {
            case ALWAYS -> true;
            case SUCCESS -> success;
            case FAILURE -> !success;
            case NEVER -> false;
        };
    }

    private boolean commitPreparedTarget(Player player,
            ItemStack target,
            ItemStack preparedTarget,
            ItemStack originalTarget,
            EnhancementTargetProvider provider,
            int expectedLevel) {
        if (target == null || preparedTarget == null || originalTarget == null || provider == null) {
            return false;
        }
        ItemMeta preparedMeta = preparedTarget.getItemMeta();
        if (preparedMeta == null || !target.setItemMeta(preparedMeta)) {
            return false;
        }
        try {
            if (provider.readLevel(player, target) == expectedLevel) {
                return true;
            }
        } catch (RuntimeException | LinkageError exception) {
            warn("目标 Provider 提交后回读失败", exception);
        }
        ItemMeta originalMeta = originalTarget.getItemMeta();
        if (originalMeta != null && !target.setItemMeta(originalMeta)) {
            warn("目标 Provider 回读失败后的目标回滚失败", null);
        }
        return false;
    }

    private List<PityView> loadPityTracks(EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider,
            VariableContext variables) {
        if (pityStateStore == null || recipe.pityTracks().isEmpty()) {
            return List.of();
        }
        List<PityView> tracks = new ArrayList<>();
        for (EnhancementRecipe.PityConfig config : recipe.pityTracks()) {
            PityView track = loadPityTrack(config, recipe, player, target, provider, variables);
            if (track.bound()) {
                tracks.add(track);
            }
        }
        return List.copyOf(tracks);
    }

    private PityView loadPityTrack(EnhancementRecipe.PityConfig pity,
            EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider,
            VariableContext variables) {
        PityScopeEnum scope = pity.counter().scope();
        String group = isolatedGroup(pity, recipe, player, target, provider);
        String key = pityKey(scope, player, target, provider);
        if (Texts.isBlank(key)) {
            return new PityView(null, null, null, 0, false, pity);
        }
        PityState state = pityStateStore.load(scope.name(), group, key);
        int counter = state == null ? 0 : Math.max(0, state.getCounter());
        Integer threshold = pity.trigger().threshold();
        if (threshold == null && pity.trigger().formula() != null) {
            double resolved = pity.trigger().formula().resolve(variables == null
                    ? VariableContext.builder(player).build() : variables);
            threshold = finitePositiveInt(resolved, 0);
        }
        boolean triggered = threshold != null && threshold > 0 && counter >= threshold;
        return new PityView(scope, group, key, counter, triggered, pity);
    }

    private String isolatedGroup(EnhancementRecipe.PityConfig pity,
            EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider) {
        String group = pity.counter().group();
        if (pity.isolate().isEmpty()) {
            return group;
        }
        StringBuilder builder = new StringBuilder(group);
        for (PityIsolationEnum dimension : pity.isolate()) {
            builder.append('#').append(dimension.token()).append('=')
                    .append(isolationValue(dimension, recipe, player, target, provider));
        }
        return builder.toString();
    }

    private String isolationValue(PityIsolationEnum dimension,
            EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider) {
        return switch (dimension) {
            case RECIPE -> Texts.lower(recipe.id());
            case MODE -> Texts.lower(recipe.mode());
            case TARGET -> Texts.lower(recipe.target().provider());
            case AFFIX -> selectedAffixKey(player);
            case LEVEL -> Integer.toString(safeReadLevel(provider, player, target));
        };
    }

    private String selectedAffixKey(Player player) {
        if (plugin == null || plugin.affixSelectionService() == null || player == null) {
            return "";
        }
        try {
            return Texts.lower(plugin.affixSelectionService().selected(player.getUniqueId(), List.of()));
        } catch (RuntimeException | LinkageError exception) {
            warn("读取词条选择用于 pity 隔离失败", exception);
            return "";
        }
    }

    private int safeReadLevel(EnhancementTargetProvider provider, Player player, ItemStack target) {
        if (provider == null || target == null) {
            return 0;
        }
        try {
            return Math.max(0, provider.readLevel(player, target));
        } catch (RuntimeException | LinkageError exception) {
            warn("读取目标等级用于 pity 隔离失败", exception);
            return 0;
        }
    }

    private List<PityView> updatePityTracks(List<PityView> tracks, boolean success) {
        if (pityStateStore == null || tracks.isEmpty()) {
            return tracks;
        }
        List<PityView> updated = new ArrayList<>(tracks.size());
        for (PityView track : tracks) {
            updated.add(updatePity(track, success));
        }
        return List.copyOf(updated);
    }

    private PityView updatePity(PityView pity, boolean success) {
        if (!pity.bound() || pityStateStore == null) {
            return pity;
        }
        EnhancementRecipe.PityConfig config = pity.config();
        int counter = pity.counter();
        if (success) {
            PityDecayTypeEnum decayType = config == null || config.decay() == null
                    ? PityDecayTypeEnum.RESET
                    : config.decay().type();
            double decayValue = config == null || config.decay() == null ? 0D : config.decay().value();
            counter = switch (decayType) {
                case RESET -> 0;
                case FIXED_DECAY -> (int) Math.max(0L,
                        (long) counter - Math.min((long) Integer.MAX_VALUE, Math.round(decayValue)));
                case PROPORTIONAL -> Math.max(0, (int) Math.floor(counter * (1D - decayValue)));
            };
        } else {
            counter = counter >= Integer.MAX_VALUE ? Integer.MAX_VALUE : counter + 1;
        }
        PityState next = new PityState(counter, System.currentTimeMillis(), pity.triggered());
        pityStateStore.save(pity.scope().name(), pity.group(), pity.key(), next);
        return new PityView(pity.scope(), pity.group(), pity.key(), counter, pity.triggered(), config);
    }

    private EnhancementPityResult toPityResult(@Nullable List<PityView> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return EnhancementPityResult.empty();
        }
        List<EnhancementPityTrack> published = new ArrayList<>(tracks.size());
        for (PityView pity : tracks) {
            if (!pity.bound()) {
                continue;
            }
            published.add(new EnhancementPityTrack(
                    pity.scope().name().toLowerCase(Locale.ROOT),
                    pity.group(),
                    pity.counter(),
                    pity.triggered()));
        }
        return EnhancementPityResult.ofTracks(published);
    }

    private boolean requiresItemPityOwner(EnhancementRecipe recipe) {
        for (EnhancementRecipe.PityConfig track : recipe.pityTracks()) {
            if (track.counter().scope() == PityScopeEnum.ITEM) {
                return true;
            }
        }
        return false;
    }

    private String pityKey(PityScopeEnum scope,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider) {
        if (scope == PityScopeEnum.PLAYER) {
            return player.getUniqueId().toString();
        }
        ItemMeta meta = target == null ? null : target.getItemMeta();
        String ownerId = meta == null ? null
                : meta.getPersistentDataContainer().get(PITY_OWNER_KEY, PersistentDataType.STRING);
        return Texts.isBlank(ownerId) ? "" : ownerId;
    }

    private static double clampChance(double value) {
        if (!Double.isFinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }

    private static int finitePositiveInt(double value, int fallback) {
        if (!Double.isFinite(value) || value <= 0D) {
            return fallback;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(value);
    }

    record MaterialMatch(ItemStack stack, int amount, ConsumeTimingEnum timing) {
    }

    private record PreparedLevel(@Nullable ItemStack itemStack, String errorKey) {

        static PreparedLevel failed(String errorKey) {
            return new PreparedLevel(null, errorKey);
        }

        boolean usable() {
            return itemStack != null;
        }
    }

    record PityView(@Nullable PityScopeEnum scope,
            @Nullable String group,
            @Nullable String key,
            int counter,
            boolean triggered,
            @Nullable EnhancementRecipe.PityConfig config) {

        PityView(@Nullable PityScopeEnum scope,
                @Nullable String group,
                @Nullable String key,
                int counter,
                boolean triggered) {
            this(scope, group, key, counter, triggered, null);
        }

        boolean bound() {
            return scope != null && Texts.isNotBlank(group) && Texts.isNotBlank(key);
        }
    }


    private record AttemptStart(boolean started,
            @Nullable EnhancementAttemptResult existingResult,
            String errorKey) {
    }


    private record JournalEntry(int fingerprint, @Nullable EnhancementAttemptResult result) {
    }

    record ExecutionPlan(boolean valid,
            EnhancementRecipe recipe,
            int currentLevel,
            int targetLevel,
            int currentTemper,
            @Nullable ItemStack preparedTarget,
            List<PityView> pityTracks,
            double baseRate,
            double effectiveRate,
            boolean forceSuccess,
            List<AttemptCost> costs,
            List<MaterialMatch> materialMatches,
            EnhancementAttemptPreview preview,
            EnhancementTargetVariables.Snapshot snapshot,
            @Nullable ItemStack failureTarget,
            int failureLevel) {

        ExecutionPlan {
            pityTracks = pityTracks == null ? List.of() : List.copyOf(pityTracks);
            costs = costs == null ? List.of() : List.copyOf(costs);
            materialMatches = materialMatches == null ? List.of() : List.copyOf(materialMatches);
        }

        static ExecutionPlan invalid(EnhancementRecipe recipe,
                String errorKey,
                EnhancementTargetVariables.Snapshot snapshot) {
            EnhancementAttemptPreview preview = EnhancementAttemptPreview.rejected(errorKey);
            return new ExecutionPlan(false, recipe, 0, 0, 0, null,
                    List.of(), 0D, 0D, false, List.of(), List.of(), preview, snapshot, null, 0);
        }

        PityView primaryPity() {
            return pityTracks.isEmpty() ? new PityView(null, null, null, 0, false) : pityTracks.get(0);
        }

        boolean pityTriggered() {
            for (PityView track : pityTracks) {
                if (track.triggered()) {
                    return true;
                }
            }
            return false;
        }
    }
}
