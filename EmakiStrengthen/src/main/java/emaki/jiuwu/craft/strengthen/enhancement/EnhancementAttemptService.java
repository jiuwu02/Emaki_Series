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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.math.CraftRollEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.craft.CraftOperationJournal;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.variable.VariableContext;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementPityResult;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementPityTrack;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;
import emaki.jiuwu.craft.strengthen.enhancement.cost.ConsumeTimingEnum;
import emaki.jiuwu.craft.strengthen.enhancement.cost.MaterialSlotConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityDecayTypeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityEffectTypeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityScopeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityState;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityStateStore;
import emaki.jiuwu.craft.strengthen.enhancement.progression.EnhancementProgressionResolver;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipe;
import emaki.jiuwu.craft.strengthen.enhancement.target.EnhancementTargetRegistry;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;

/**
 * 执行强化框架配方的服务。
 *
 * <p>所有目标类型共用材料、费用、概率、保底和事务阶段；目标层的具体读写仍完全由
 * {@link EnhancementTargetProvider} 负责。调用方必须在持有目标物品的实体线程调用，本服务不自行调度。
 */
public final class EnhancementAttemptService {

    private static final int MAX_SAFE_LEVEL = 1_000_000;
    private static final String OPERATION_NAMESPACE = "strengthen_enhancement";
    private static final int MAX_JOURNAL_ENTRIES = 256;
    private static final NamespacedKey PITY_OWNER_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("emakistrengthen:pity_owner_id"));

    private final EmakiStrengthenPlugin plugin;
    private final EnhancementTargetRegistry targetRegistry;
    private final PityStateStore pityStateStore;
    private final EnhancementProgressionResolver progressionResolver;
    private final CraftOperationJournal<JournalEntry> operationJournal =
            CraftOperationJournal.ofMemory(MAX_JOURNAL_ENTRIES);
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public EnhancementAttemptService(EmakiStrengthenPlugin plugin,
            EnhancementTargetRegistry targetRegistry,
            PityStateStore pityStateStore,
            EnhancementProgressionResolver progressionResolver) {
        this.plugin = plugin;
        this.targetRegistry = targetRegistry;
        this.pityStateStore = pityStateStore;
        this.progressionResolver = Objects.requireNonNull(progressionResolver, "progressionResolver");
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

    /** 使用自动生成的 operation id 执行一次强化。 */
    public @NotNull EnhancementAttemptResult attempt(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied) {
        return attempt(player, recipe, target, supplied, UUID.randomUUID().toString());
    }

    /**
     * 执行一次强化尝试。
     *
     * <p>Provider 写回先在目标副本上完成并回读确认，之后才扣费；最终扣费、材料提交和目标 ItemMeta
     * 提交任一阶段失败都会恢复材料并尝试退款。这样容量不足、Provider 静默失败不会造成先扣费。
     */
    public @NotNull EnhancementAttemptResult attempt(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied,
            @Nullable String operationId) {
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
            result = attemptOnce(player, recipe, target, supplied, operation);
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
            @NotNull String operation) {
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
        if (recipe.pity() != null && recipe.pity().counter().scope() == PityScopeEnum.ITEM
                && !ensurePityOwner(target)) {
            return rejectedWithOperation("strengthen.error.rebuild_failed", operation);
        }
        List<ItemStack> suppliedItems = supplied == null ? List.of() : supplied;
        ExecutionPlan plan = prepare(player, recipe, target, suppliedItems, provider);
        if (!plan.valid()) {
            return rejectedPlan(plan, operation);
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
        try {
            consumeMaterials(plan.materialMatches(), success);
            if (success && !commitPreparedTarget(player, target, plan.preparedTarget(), originalTarget,
                    provider, plan.targetLevel())) {
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

        PityView updated;
        try {
            // pity 只有在费用、材料和目标提交均完成后才推进。
            updated = updatePity(plan.recipe(), plan.pity(), success);
        } catch (RuntimeException | LinkageError exception) {
            // 目标已经提交，不能再伪装成未提交；记录异常并保留本次结果。
            warn("强化框架保底状态写回失败", exception);
            updated = plan.pity();
        }
        int resultingLevel = success ? plan.targetLevel() : plan.currentLevel();
        Map<String, String> resultPlaceholders = Map.of("operation_id", operation);
        EnhancementPityResult pityResult = toPityResult(updated);
        EnhancementAttemptResult result = new EnhancementAttemptResult(true, success, "", resultPlaceholders,
                plan.currentLevel(), resultingLevel, plan.effectiveRate(), pityResult);
        if (plugin.actionCoordinator() != null) {
            plugin.actionCoordinator().triggerEnhancementActions(player, recipe, target, result, operation);
        }
        return result;
    }

    /**
     * 解析一次尝试的只读预览。该方法不会扣费、扣材料、推进 pity 或改动原目标。
     */
    public @NotNull EnhancementAttemptPreview preview(@Nullable Player player,
            @Nullable EnhancementRecipe recipe,
            @Nullable ItemStack target,
            @Nullable List<ItemStack> supplied) {
        if (player == null || !player.isOnline()) {
            return EnhancementAttemptPreview.rejected("strengthen.enhancement.no_player");
        }
        if (recipe == null) {
            return EnhancementAttemptPreview.rejected("strengthen.error.no_recipe");
        }
        if (target == null || target.getType().isAir()) {
            return EnhancementAttemptPreview.rejected("strengthen.error.no_target");
        }
        EnhancementTargetProvider provider = resolveProvider(player, recipe, target);
        if (provider == null) {
            return EnhancementAttemptPreview.rejected("strengthen.enhancement.provider_not_found");
        }
        return prepare(player, recipe, target, supplied == null ? List.of() : supplied, provider).preview();
    }

    /** 解析配方声明的目标 Provider；不通过 canHandle 猜测 Provider。 */
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
        int currentLevel = snapshot.level();
        int currentTemper = snapshot.temper();
        if (currentLevel >= MAX_SAFE_LEVEL) {
            return ExecutionPlan.invalid(recipe, "strengthen.error.rebuild_failed", snapshot);
        }

        VariableContext targetVariables = buildVariablesFromSnapshot(player, snapshot, currentLevel);
        if (!matchesTargetFilter(recipe, target, targetVariables)) {
            return ExecutionPlan.invalid(recipe, "strengthen.error.rebuild_failed", snapshot);
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
                recipe, player, target, supplied, variables, materialAmounts);
        List<AttemptCost> costs = progression.currentCosts();
        PityView pity = loadPity(recipe, player, target, provider, variables);
        double baseRate = clampChance(progression.chance().current());
        double effectiveRate = baseRate;
        boolean forceSuccess = false;
        if (pity.triggered()) {
            PityEffectTypeEnum effectType = recipe.pity().effect().type();
            if (effectType == PityEffectTypeEnum.FORCE_SUCCESS) {
                forceSuccess = true;
                effectiveRate = 1D;
            } else if (effectType == PityEffectTypeEnum.CHANCE_BONUS) {
                Double bonus = recipe.pity().effect().bonusValue();
                effectiveRate = clampChance(baseRate + (bonus == null ? 0D : bonus));
            }
        }

        List<EnhancementAttemptPreview.MaterialRequirement> requirements = materialRequirements(
                recipe, player, target, supplied, variables, materialAmounts);
        if (materialMatches == null) {
            EnhancementAttemptPreview preview = EnhancementAttemptPreview.invalid(
                    "strengthen.error.material_missing", currentLevel, targetLevel,
                    baseRate, effectiveRate, pity.counter(), pity.triggered(), costs, requirements);
            return new ExecutionPlan(false, recipe, currentLevel, targetLevel, currentTemper, null, pity,
                    baseRate, effectiveRate, forceSuccess, costs, List.of(), preview, snapshot);
        }

        if (targetLevel <= currentLevel || targetLevel > MAX_SAFE_LEVEL) {
            EnhancementAttemptPreview preview = EnhancementAttemptPreview.invalid(
                    "strengthen.error.rebuild_failed", currentLevel, targetLevel,
                    baseRate, effectiveRate, pity.counter(), pity.triggered(), costs, requirements);
            return new ExecutionPlan(false, recipe, currentLevel, targetLevel, currentTemper, null, pity,
                    baseRate, effectiveRate, forceSuccess, costs, materialMatches, preview, snapshot);
        }

        ItemStack preparedTarget;
        try {
            preparedTarget = target.clone();
            provider.writeLevel(player, preparedTarget, targetLevel);
            int readBack = provider.readLevel(player, preparedTarget);
            if (readBack != targetLevel) {
                EnhancementAttemptPreview preview = EnhancementAttemptPreview.invalid(
                        "strengthen.error.rebuild_failed", currentLevel, targetLevel,
                        baseRate, effectiveRate, pity.counter(), pity.triggered(), costs, requirements);
                return new ExecutionPlan(false, recipe, currentLevel, targetLevel, currentTemper, null, pity,
                        baseRate, effectiveRate, forceSuccess, costs, materialMatches, preview, snapshot);
            }
        } catch (RuntimeException | LinkageError exception) {
            warn("目标 Provider 预写回失败", exception);
            EnhancementAttemptPreview preview = EnhancementAttemptPreview.invalid(
                    "strengthen.error.rebuild_failed", currentLevel, targetLevel,
                    baseRate, effectiveRate, pity.counter(), pity.triggered(), costs, requirements);
            return new ExecutionPlan(false, recipe, currentLevel, targetLevel, currentTemper, null, pity,
                    baseRate, effectiveRate, forceSuccess, costs, materialMatches, preview, snapshot);
        }

        EnhancementAttemptPreview preview = EnhancementAttemptPreview.valid(
                recipe.id(), currentLevel, targetLevel, baseRate, effectiveRate,
                pity.counter(), pity.triggered(), costs, requirements);
        return new ExecutionPlan(true, recipe, currentLevel, targetLevel, currentTemper, preparedTarget, pity,
                baseRate, effectiveRate, forceSuccess, costs, materialMatches, preview, snapshot);
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


    /** 把配方的每个材料槽匹配到玩家提供的物品上。 */
    private @Nullable List<MaterialMatch> matchMaterials(EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            List<ItemStack> supplied,
            VariableContext variables,
            List<Integer> requiredAmounts) {
        if (recipe.materials().isEmpty()) {
            return List.of();
        }
        List<MaterialMatch> matches = new ArrayList<>();
        Map<ItemStack, Integer> consumedPerStack = new IdentityHashMap<>();
        // 目标在整轮匹配中不变，只识别一次；候选逐个识别。
        ItemSourceRef targetSource = identifySource(target);
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
                if (available <= 0 || !testMatcher(slot, candidate, target, targetSource, player, variables)) {
                    continue;
                }
                int take = Math.min(available, remaining);
                slotMatches.add(new MaterialMatch(candidate, take, slot.consumeTiming()));
                consumedPerStack.merge(candidate, take, Integer::sum);
                remaining -= take;
            }
            if (remaining > 0) {
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
            VariableContext variables) {
        try {
            // item_source / compare_target 匹配器依赖已识别的物品源；缺失时它们只会一律判否。
            MatchContext context = new MatchContext(candidate, identifySource(candidate), player,
                    target, targetSource, variables);
            return slot.matcher().test(context);
        } catch (RuntimeException | LinkageError exception) {
            warn("材料 Matcher 判定抛出异常，视为不匹配", exception);
            return false;
        }
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

    /** {@return 物品对应的物品源引用；无法识别时为 {@code null}} */
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
            List<Integer> requiredAmounts) {
        List<EnhancementAttemptPreview.MaterialRequirement> result = new ArrayList<>();
        ItemSourceRef targetSource = identifySource(target);
        for (int index = 0; index < recipe.materials().size(); index++) {
            MaterialSlotConfig slot = recipe.materials().get(index);
            int required = index < requiredAmounts.size()
                    ? Math.max(0, requiredAmounts.get(index))
                    : 0;
            int available = 0;
            if (supplied != null) {
                for (ItemStack candidate : supplied) {
                    if (candidate != null && !candidate.getType().isAir()
                            && testMatcher(slot, candidate, target, targetSource, player, variables)) {
                        available = saturatedAdd(available, candidate.getAmount());
                    }
                }
            }
            result.add(new EnhancementAttemptPreview.MaterialRequirement(
                    "material_" + (index + 1), required, available, slot.consumeTiming()));
        }
        return List.copyOf(result);
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

    /** 按 {@link ConsumeTimingEnum} 实际扣减材料数量。 */
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

    /** 读取当前保底状态，并判断本次是否已达触发条件。 */
    private PityView loadPity(EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider,
            VariableContext variables) {
        EnhancementRecipe.PityConfig pity = recipe.pity();
        if (pity == null || pityStateStore == null) {
            return new PityView(null, null, null, 0, false);
        }
        PityScopeEnum scope = pity.counter().scope();
        String group = pity.counter().group();
        String key = pityKey(scope, player, target, provider);
        if (Texts.isBlank(key)) {
            return new PityView(null, null, null, 0, false);
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
        return new PityView(scope, group, key, counter, triggered);
    }

    /** 按成败推进保底计数。 */
    private PityView updatePity(EnhancementRecipe recipe, PityView pity, boolean success) {
        if (pity.scope() == null || pityStateStore == null) {
            return pity;
        }
        int counter = pity.counter();
        if (success) {
            PityDecayTypeEnum decayType = recipe.pity().decay() == null
                    ? PityDecayTypeEnum.RESET
                    : recipe.pity().decay().type();
            double decayValue = recipe.pity().decay() == null ? 0D : recipe.pity().decay().value();
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
        return new PityView(pity.scope(), pity.group(), pity.key(), counter, pity.triggered());
    }

    private EnhancementPityResult toPityResult(@Nullable PityView pity) {
        if (pity == null || pity.scope() == null || Texts.isBlank(pity.group()) || Texts.isBlank(pity.key())) {
            return EnhancementPityResult.empty();
        }
        EnhancementPityTrack track = new EnhancementPityTrack(
                pity.scope().name().toLowerCase(Locale.ROOT),
                pity.group(),
                pity.counter(),
                pity.triggered());
        return EnhancementPityResult.of(track);
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

    private record MaterialMatch(ItemStack stack, int amount, ConsumeTimingEnum timing) {
    }

    private record PityView(@Nullable PityScopeEnum scope,
            @Nullable String group,
            @Nullable String key,
            int counter,
            boolean triggered) {
    }


    private record AttemptStart(boolean started,
            @Nullable EnhancementAttemptResult existingResult,
            String errorKey) {
    }


    private record JournalEntry(int fingerprint, @Nullable EnhancementAttemptResult result) {
    }

    private record ExecutionPlan(boolean valid,
            EnhancementRecipe recipe,
            int currentLevel,
            int targetLevel,
            int currentTemper,
            @Nullable ItemStack preparedTarget,
            PityView pity,
            double baseRate,
            double effectiveRate,
            boolean forceSuccess,
            List<AttemptCost> costs,
            List<MaterialMatch> materialMatches,
            EnhancementAttemptPreview preview,
            EnhancementTargetVariables.Snapshot snapshot) {

        static ExecutionPlan invalid(EnhancementRecipe recipe, String errorKey, EnhancementTargetVariables.Snapshot snapshot) {
            EnhancementAttemptPreview preview = EnhancementAttemptPreview.rejected(errorKey);
            return new ExecutionPlan(false, recipe, 0, 0, 0, null,
                    new PityView(null, null, null, 0, false), 0D, 0D, false, List.of(), List.of(), preview, snapshot);
        }
    }
}
