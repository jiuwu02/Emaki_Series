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
import emaki.jiuwu.craft.corelib.matcher.Matcher;
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
    static final String ERROR_COMPENSATION_PENDING = "strengthen.error.compensation_pending";
    static final String ERROR_REBUILD_FAILED = "strengthen.error.rebuild_failed";
    private static final String ERROR_TARGET_CHANGED = "strengthen.enhancement.target_changed";
    private static final String ERROR_CONDITION_NOT_MET = "strengthen.error.condition_not_met";
    static final String ERROR_CONTAINER_TARGET = "strengthen.enhancement.container_target_rejected";
    static final String ERROR_CONTAINER_MATERIAL = "strengthen.enhancement.container_material_rejected";
    static final String DEBUG_MODULE = "attempt";
    static final NamespacedKey PITY_OWNER_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("emakistrengthen:pity_owner_id"));

    final EmakiStrengthenPlugin plugin;
    final EnhancementTargetRegistry targetRegistry;
    final PityStateStore pityStateStore;
    private final EnhancementProgressionResolver progressionResolver;
    final AffixLayerCodec affixLayerCodec;
    private final MasteryProgressService masteryProgressService;
    final CraftOperationJournal<JournalEntry> operationJournal =
            CraftOperationJournal.ofMemory(MAX_JOURNAL_ENTRIES);
    final AtomicBoolean accepting = new AtomicBoolean(true);

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

    public boolean accepting() { return new EnhancementAttemptServiceDelegate(this).accepting(); }

    public void freezeAccepting() { new EnhancementAttemptServiceDelegate(this).freezeAccepting(); }

    public void resumeAccepting() { new EnhancementAttemptServiceDelegate(this).resumeAccepting(); }

    public boolean drain(long timeout, TimeUnit unit) { return new EnhancementAttemptServiceDelegate(this).drain(timeout, unit); }

    public @NotNull Map<String, String> journalSnapshot() { return new EnhancementAttemptServiceDelegate(this).journalSnapshot(); }

    public @NotNull Map<String, String> journalPhases() { return new EnhancementAttemptServiceDelegate(this).journalPhases(); }

    public @Nullable EnhancementOperationView operationView(@Nullable String operationId) { return new EnhancementAttemptServiceDelegate(this).operationView(operationId); }

    public @NotNull List<EnhancementOperationView> operationViews() { return new EnhancementAttemptServiceDelegate(this).operationViews(); }

    @Nullable EnhancementOperationView toOperationView(String journalKey,
            CraftOperationJournal.Entry<JournalEntry> entry) { return new EnhancementAttemptServiceDelegate(this).toOperationView(journalKey, entry); }


    public @NotNull EnhancementAttemptResult attempt(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied) { return new EnhancementAttemptServiceDelegate(this).attempt(player, recipe, target, supplied); }

    public @NotNull EnhancementAttemptResult attempt(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied,
            @Nullable String operationId) { return new EnhancementAttemptServiceDelegate(this).attempt(player, recipe, target, supplied, operationId); }

    public @NotNull EnhancementAttemptResult attempt(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied,
            @Nullable String operationId,
            @Nullable EnhancementTargetVariables.Snapshot expectedSnapshot) { return new EnhancementAttemptServiceDelegate(this).attempt(player, recipe, target, supplied, operationId, expectedSnapshot); }

    public @NotNull EnhancementAttemptResult attemptWithPreview(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied,
            @Nullable String operationId,
            @Nullable EnhancementPreviewSession previewSession) { return new EnhancementAttemptServiceDelegate(this).attemptWithPreview(player, recipe, target, supplied, operationId, previewSession); }

    @NotNull EnhancementAttemptResult attempt(@Nullable Player player,
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
            @Nullable List<ItemStack> supplied) { return new EnhancementAttemptServiceDelegate(this).preview(player, recipe, target, supplied); }

    public @NotNull EnhancementPreviewSession previewSession(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied) { return new EnhancementAttemptServiceDelegate(this).previewSession(player, recipe, target, supplied); }

    @Nullable ExecutionPlan reusablePlan(Player player,
            ItemStack target,
            EnhancementTargetProvider provider,
            @Nullable ExecutionPlan candidate) { return new EnhancementAttemptServiceDelegate(this).reusablePlan(player, target, provider, candidate); }

    @Nullable EnhancementTargetProvider resolveProvider(@Nullable Player player,
            EnhancementRecipe recipe,
            ItemStack target) { return new EnhancementAttemptServiceDelegate(this).resolveProvider(player, recipe, target); }

    ExecutionPlan prepare(Player player,
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
        if (!matchesTargetFilter(recipe, player, target, targetVariables)) {
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
        double baseRate = EnhancementAttemptServiceSupport.clampChance(progression.chance().current());
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
                effectiveRate = EnhancementAttemptServiceSupport.clampChance(effectiveRate + (bonus == null ? 0D : bonus));
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

    PreparedLevel prepareLevelWriteBack(Player player,
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

    private String journalKey(@Nullable Player player, String operationId) { return new EnhancementAttemptServiceDelegate(this).journalKey(player, operationId); }

    private int attemptFingerprint(@Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied) { return new EnhancementAttemptServiceDelegate(this).attemptFingerprint(recipe, target, supplied); }

    AttemptStart beginOperation(String journalKey, @Nullable UUID playerId, int fingerprint) {
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

    private void completeOperation(String journalKey, int fingerprint, EnhancementAttemptResult result) { new EnhancementAttemptServiceDelegate(this).completeOperation(journalKey, fingerprint, result); }

    private void finishInFlight(String journalKey) { new EnhancementAttemptServiceDelegate(this).finishInFlight(journalKey); }

    void pruneJournal() { new EnhancementAttemptServiceDelegate(this).pruneJournal(); }

    EnhancementAttemptResult rejectedPlan(ExecutionPlan plan, String operation) { return new EnhancementAttemptServiceDelegate(this).rejectedPlan(plan, operation); }

    EnhancementAttemptResult rejectedWithOperation(String errorKey, String operation) { return new EnhancementAttemptServiceDelegate(this).rejectedWithOperation(errorKey, operation); }

    private EnhancementAttemptResult compensateAfterCommitFailure(Player player,
            StrengthenEconomyService economy,
            List<AttemptCost> appliedCosts,
            String operation,
            String fallbackError) { return new EnhancementAttemptServiceDelegate(this).compensateAfterCommitFailure(player, economy, appliedCosts, operation, fallbackError); }

    void warn(String message, Throwable throwable) { new EnhancementAttemptServiceDelegate(this).warn(message, throwable); }

    void debug(@Nullable Player player, String langKey, Map<String, ?> replacements) {
        if (plugin == null || plugin.debugLogger() == null) {
            return;
        }
        plugin.debugLogger().log(DEBUG_MODULE, player, langKey, replacements);
    }

    private @NotNull String refreshPreparedPresentation(Player player,
            ItemStack preparedTarget,
            EnhancementTargetProvider provider) { return new EnhancementAttemptServiceDelegate(this).refreshPreparedPresentation(player, preparedTarget, provider); }

    private boolean satisfiesRecipeConditions(EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            VariableContext variables) { return new EnhancementAttemptServiceDelegate(this).satisfiesRecipeConditions(recipe, player, target, variables); }

    private boolean sameFrozenTarget(@Nullable EnhancementTargetVariables.Snapshot expected,
            @Nullable EnhancementTargetVariables.Snapshot actual,
            Player player,
            String operation) { return new EnhancementAttemptServiceDelegate(this).sameFrozenTarget(expected, actual, player, operation); }

    private boolean containerBackedTarget(Player player, ItemStack target) { return new EnhancementAttemptServiceDelegate(this).containerBackedTarget(player, target); }

    private void logContainerRejection(Player player, ItemStack target) { new EnhancementAttemptServiceDelegate(this).logContainerRejection(player, target); }

    private void reportUnreadablePdc(Player player, EnhancementTargetVariables.Snapshot snapshot) { new EnhancementAttemptServiceDelegate(this).reportUnreadablePdc(player, snapshot); }


    private VariableContext buildVariablesFromSnapshot(Player player,
            EnhancementTargetVariables.Snapshot snapshot,
            int evaluationLevel) { return new EnhancementAttemptServiceDelegate(this).buildVariablesFromSnapshot(player, snapshot, evaluationLevel); }

    @Nullable List<MaterialMatch> matchMaterials(EnhancementRecipe recipe,
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
                slotMatches.add(new MaterialMatch(candidate, take, slot.consumeTiming(),
                        slot.materialId(), slot.countKey(), slotIndex));
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

    boolean testMatcher(MaterialSlotConfig slot,
            ItemStack candidate,
            ItemStack target,
            @Nullable ItemSourceRef targetSource,
            Player player,
            VariableContext variables,
            int targetLevel,
            EnhancementTargetProvider provider,
            Map<ItemStack, VariableContext> comparisonVariables) { return new EnhancementAttemptServiceDelegate(this).testMatcher(slot, candidate, target, targetSource, player, variables, targetLevel, provider, comparisonVariables); }


    private boolean matchesTargetFilter(EnhancementRecipe recipe,
            @Nullable Player player,
            ItemStack target,
            VariableContext variables) { return new EnhancementAttemptServiceDelegate(this).matchesTargetFilter(recipe, player, target, variables); }

    boolean testTargetFilterMatcher(Matcher filterMatcher,
            @Nullable Player player,
            ItemStack target,
            VariableContext variables) { return new EnhancementAttemptServiceDelegate(this).testTargetFilterMatcher(filterMatcher, player, target, variables); }


    private boolean ensurePityOwner(ItemStack target) { return new EnhancementAttemptServiceDelegate(this).ensurePityOwner(target); }


    @Nullable ItemSourceRef identifySource(@Nullable ItemStack itemStack) { return new EnhancementAttemptServiceDelegate(this).identifySource(itemStack); }

    private List<EnhancementAttemptPreview.MaterialRequirement> materialRequirements(
            EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            List<ItemStack> supplied,
            VariableContext variables,
            List<Integer> requiredAmounts,
            int targetLevel,
            EnhancementTargetProvider provider) { return new EnhancementAttemptServiceDelegate(this).materialRequirements(recipe, player, target, supplied, variables, requiredAmounts, targetLevel, provider); }

    private boolean containerBackedMaterials(Player player, List<ItemStack> supplied) { return new EnhancementAttemptServiceDelegate(this).containerBackedMaterials(player, supplied); }

    private void logContainerMaterialRejection(Player player, int suppliedCount) { new EnhancementAttemptServiceDelegate(this).logContainerMaterialRejection(player, suppliedCount); }

    private List<AttemptCost> applyCostGrowth(List<AttemptCost> costs, int currentLevel) { return new EnhancementAttemptServiceDelegate(this).applyCostGrowth(costs, currentLevel); }

    private double applyDiminishingReturns(double baseRate, int currentLevel) { return new EnhancementAttemptServiceDelegate(this).applyDiminishingReturns(baseRate, currentLevel); }

    private double applyMinimumSuccessRate(double rate) { return new EnhancementAttemptServiceDelegate(this).applyMinimumSuccessRate(rate); }

    private int resolveFailureLevel(int currentLevel) { return new EnhancementAttemptServiceDelegate(this).resolveFailureLevel(currentLevel); }

    private StrengthenEconomyService.ChargeResult chargeCurrencies(Player player,
            List<AttemptCost> costs,
            StrengthenEconomyService economy,
            String operation) { return new EnhancementAttemptServiceDelegate(this).chargeCurrencies(player, costs, economy, operation); }

    Map<ItemStack, Integer> snapshotAmounts(List<MaterialMatch> matches) { return new EnhancementAttemptServiceDelegate(this).snapshotAmounts(matches); }

    private void restoreAmounts(Map<ItemStack, Integer> snapshot) { new EnhancementAttemptServiceDelegate(this).restoreAmounts(snapshot); }

    void consumeMaterials(List<MaterialMatch> matches, boolean success) { new EnhancementAttemptServiceDelegate(this).consumeMaterials(matches, success); }


    private boolean commitPreparedTarget(Player player,
            ItemStack target,
            ItemStack preparedTarget,
            ItemStack originalTarget,
            EnhancementTargetProvider provider,
            int expectedLevel) { return new EnhancementAttemptServiceDelegate(this).commitPreparedTarget(player, target, preparedTarget, originalTarget, provider, expectedLevel); }

    List<PityView> loadPityTracks(EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider,
            VariableContext variables) { return new EnhancementAttemptServiceDelegate2(this).loadPityTracks(recipe, player, target, provider, variables); }

    PityView loadPityTrack(EnhancementRecipe.PityConfig pity,
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
            threshold = EnhancementAttemptServiceSupport.finitePositiveInt(resolved, 0);
        }
        boolean triggered = threshold != null && threshold > 0 && counter >= threshold;
        return new PityView(scope, group, key, counter, triggered, pity);
    }

    private String isolatedGroup(EnhancementRecipe.PityConfig pity,
            EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider) { return new EnhancementAttemptServiceDelegate2(this).isolatedGroup(pity, recipe, player, target, provider); }

    String isolationValue(PityIsolationEnum dimension,
            EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider) { return new EnhancementAttemptServiceDelegate2(this).isolationValue(dimension, recipe, player, target, provider); }

    String selectedAffixKey(Player player) { return new EnhancementAttemptServiceDelegate2(this).selectedAffixKey(player); }

    int safeReadLevel(EnhancementTargetProvider provider, Player player, ItemStack target) { return new EnhancementAttemptServiceDelegate2(this).safeReadLevel(provider, player, target); }

    List<PityView> updatePityTracks(List<PityView> tracks, boolean success) { return new EnhancementAttemptServiceDelegate2(this).updatePityTracks(tracks, success); }

    PityView updatePity(PityView pity, boolean success) { return new EnhancementAttemptServiceDelegate2(this).updatePity(pity, success); }

    EnhancementPityResult toPityResult(@Nullable List<PityView> tracks) { return new EnhancementAttemptServiceDelegate2(this).toPityResult(tracks); }

    private boolean requiresItemPityOwner(EnhancementRecipe recipe) { return new EnhancementAttemptServiceDelegate2(this).requiresItemPityOwner(recipe); }

    private String pityKey(PityScopeEnum scope,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider) { return new EnhancementAttemptServiceDelegate2(this).pityKey(scope, player, target, provider); }


    record MaterialMatch(ItemStack stack,
            int amount,
            ConsumeTimingEnum timing,
            String materialId,
            String countKey,
            int slotIndex) {
    }

    record PreparedLevel(@Nullable ItemStack itemStack, String errorKey) {

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

    record AttemptStart(boolean started,
            @Nullable EnhancementAttemptResult existingResult,
            String errorKey) {
    }

    record JournalEntry(int fingerprint, @Nullable EnhancementAttemptResult result) {
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
