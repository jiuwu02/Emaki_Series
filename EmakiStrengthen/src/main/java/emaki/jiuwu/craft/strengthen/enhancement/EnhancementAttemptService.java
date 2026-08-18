package emaki.jiuwu.craft.strengthen.enhancement;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.math.CraftRollEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;
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
import emaki.jiuwu.craft.strengthen.enhancement.pity.InMemoryPityStateStore;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityDecayTypeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityEffectTypeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityScopeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityState;
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

    private final EmakiStrengthenPlugin plugin;
    private final EnhancementTargetRegistry targetRegistry;
    private final InMemoryPityStateStore pityStateStore;
    private final EnhancementProgressionResolver progressionResolver;

    public EnhancementAttemptService(EmakiStrengthenPlugin plugin,
            EnhancementTargetRegistry targetRegistry,
            InMemoryPityStateStore pityStateStore,
            EnhancementProgressionResolver progressionResolver) {
        this.plugin = plugin;
        this.targetRegistry = targetRegistry;
        this.pityStateStore = pityStateStore;
        this.progressionResolver = Objects.requireNonNull(progressionResolver, "progressionResolver");
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
        try {
            consumeMaterials(plan.materialMatches(), success);
            if (success && !commitPreparedTarget(target, plan.preparedTarget())) {
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
        int currentLevel;
        int currentTemper;
        try {
            currentLevel = Math.max(0, provider.readLevel(player, target));
            currentTemper = Math.max(0, provider.readTemper(player, target));
        } catch (RuntimeException | LinkageError exception) {
            warn("目标 Provider 读取当前状态失败", exception);
            return ExecutionPlan.invalid(recipe, "strengthen.error.rebuild_failed");
        }

        EnhancementProgressionResolver.Resolution progression;
        try {
            progression = progressionResolver.resolve(recipe, currentLevel,
                    evaluationLevel -> buildVariables(
                            player, target, currentLevel, currentTemper, evaluationLevel));
        } catch (RuntimeException | LinkageError exception) {
            warn("强化进度解析失败", exception);
            return ExecutionPlan.invalid(recipe, "strengthen.error.rebuild_failed");
        }
        int targetLevel = progression.levels().targetLevel();
        VariableContext variables = progression.variables();
        List<Integer> materialAmounts = progression.currentMaterialAmounts();
        List<MaterialMatch> materialMatches = matchMaterials(
                recipe, player, target, supplied, variables, materialAmounts);
        List<AttemptCost> costs = progression.currentCosts();
        PityView pity = loadPity(recipe, player, target, provider);
        double baseRate = CraftRollEngine.clamp(progression.chance().current());
        double effectiveRate = baseRate;
        boolean forceSuccess = false;
        if (pity.triggered()) {
            PityEffectTypeEnum effectType = recipe.pity().effect().type();
            if (effectType == PityEffectTypeEnum.FORCE_SUCCESS) {
                forceSuccess = true;
                effectiveRate = 1D;
            } else if (effectType == PityEffectTypeEnum.CHANCE_BONUS) {
                Double bonus = recipe.pity().effect().bonusValue();
                effectiveRate = CraftRollEngine.clamp(baseRate + (bonus == null ? 0D : bonus));
            }
        }

        List<EnhancementAttemptPreview.MaterialRequirement> requirements = materialRequirements(
                recipe, supplied, materialAmounts);
        if (materialMatches == null) {
            EnhancementAttemptPreview preview = EnhancementAttemptPreview.invalid(
                    "strengthen.error.material_missing", currentLevel, targetLevel,
                    baseRate, effectiveRate, pity.counter(), pity.triggered(), costs, requirements);
            return new ExecutionPlan(false, recipe, currentLevel, targetLevel, currentTemper, null, pity,
                    baseRate, effectiveRate, forceSuccess, costs, List.of(), preview);
        }

        if (targetLevel <= currentLevel) {
            EnhancementAttemptPreview preview = EnhancementAttemptPreview.invalid(
                    "strengthen.error.rebuild_failed", currentLevel, targetLevel,
                    baseRate, effectiveRate, pity.counter(), pity.triggered(), costs, requirements);
            return new ExecutionPlan(false, recipe, currentLevel, targetLevel, currentTemper, null, pity,
                    baseRate, effectiveRate, forceSuccess, costs, materialMatches, preview);
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
                        baseRate, effectiveRate, forceSuccess, costs, materialMatches, preview);
            }
        } catch (RuntimeException | LinkageError exception) {
            warn("目标 Provider 预写回失败", exception);
            EnhancementAttemptPreview preview = EnhancementAttemptPreview.invalid(
                    "strengthen.error.rebuild_failed", currentLevel, targetLevel,
                    baseRate, effectiveRate, pity.counter(), pity.triggered(), costs, requirements);
            return new ExecutionPlan(false, recipe, currentLevel, targetLevel, currentTemper, null, pity,
                    baseRate, effectiveRate, forceSuccess, costs, materialMatches, preview);
        }

        EnhancementAttemptPreview preview = EnhancementAttemptPreview.valid(
                recipe.id(), currentLevel, targetLevel, baseRate, effectiveRate,
                pity.counter(), pity.triggered(), costs, requirements);
        return new ExecutionPlan(true, recipe, currentLevel, targetLevel, currentTemper, preparedTarget, pity,
                baseRate, effectiveRate, forceSuccess, costs, materialMatches, preview);
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

    private VariableContext buildVariables(Player player,
            ItemStack target,
            int currentLevel,
            int temper,
            int evaluationLevel) {
        int targetLevel = currentLevel == Integer.MAX_VALUE ? Integer.MAX_VALUE : currentLevel + 1;
        int previousLevel = Math.max(0, currentLevel - 1);
        VariableContext.Builder builder = VariableContext.builder(player)
                // Legacy aliases keep their existing meaning for the current-level resolution.
                .with("target.level", evaluationLevel)
                .with("target.temper", temper)
                .with("target_level", evaluationLevel)
                .with("target_temper", temper)
                // Explicit level window aliases support formula progressions without +1/-1 duplication.
                .with("current_level", currentLevel)
                .with("previous_level", previousLevel)
                .with("resulting_level", targetLevel)
                .with("next_level", targetLevel)
                .with("target.current_level", currentLevel)
                .with("target.previous_level", previousLevel)
                .with("target.resulting_level", targetLevel);
        readItemPdcVariables(builder, target);
        return builder.build();
    }

    /** 将目标 ItemMeta 的 PDC 暴露给公式/Matcher，不依赖 Forge runtime 类。 */
    private void readItemPdcVariables(VariableContext.Builder builder, ItemStack target) {
        if (builder == null || target == null) {
            return;
        }
        ItemMeta meta = target.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) {
            Object value = readPdcValue(container, key);
            if (value == null) {
                continue;
            }
            String generic = "item_pdc_" + key.getNamespace() + "_" + key.getKey();
            builder.with(generic, value);
            addForgeAliases(builder, key, value);
        }
    }

    private Object readPdcValue(PersistentDataContainer container, NamespacedKey key) {
        try {
            if (container.has(key, PersistentDataType.STRING)) {
                return container.get(key, PersistentDataType.STRING);
            }
            if (container.has(key, PersistentDataType.DOUBLE)) {
                return container.get(key, PersistentDataType.DOUBLE);
            }
            if (container.has(key, PersistentDataType.FLOAT)) {
                return container.get(key, PersistentDataType.FLOAT);
            }
            if (container.has(key, PersistentDataType.LONG)) {
                return container.get(key, PersistentDataType.LONG);
            }
            if (container.has(key, PersistentDataType.INTEGER)) {
                return container.get(key, PersistentDataType.INTEGER);
            }
            if (container.has(key, PersistentDataType.SHORT)) {
                return container.get(key, PersistentDataType.SHORT);
            }
            if (container.has(key, PersistentDataType.BYTE)) {
                return container.get(key, PersistentDataType.BYTE);
            }
        } catch (RuntimeException | LinkageError exception) {
            warn("读取目标物品 PDC 变量失败: " + key, exception);
        }
        return null;
    }

    private void addForgeAliases(VariableContext.Builder builder, NamespacedKey key, Object value) {
        if (!"emakiforge".equalsIgnoreCase(key.getNamespace())) {
            return;
        }
        String path = key.getKey().toLowerCase();
        switch (path) {
            case "forge.quality_id" -> {
                builder.with("forge.quality_id", value).with("quality_id", value);
            }
            case "forge.quality_display" -> {
                builder.with("forge.quality_display", value).with("quality_display", value);
            }
            case "forge.quality_multiplier" -> {
                builder.with("forge.quality_multiplier", value).with("quality_multiplier", value);
            }
            case "forge.forge_recipe_id" -> {
                builder.with("forge.forge_recipe_id", value).with("forge_recipe_id", value);
            }
            default -> {
            }
        }
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
            List<ItemStack> supplied,
            List<Integer> requiredAmounts) {
        List<EnhancementAttemptPreview.MaterialRequirement> result = new ArrayList<>();
        for (int index = 0; index < recipe.materials().size(); index++) {
            MaterialSlotConfig slot = recipe.materials().get(index);
            int required = index < requiredAmounts.size()
                    ? Math.max(0, requiredAmounts.get(index))
                    : 0;
            int available = 0;
            if (supplied != null) {
                for (ItemStack candidate : supplied) {
                    if (candidate != null && !candidate.getType().isAir()) {
                        available += candidate.getAmount();
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

    private boolean commitPreparedTarget(ItemStack target, ItemStack preparedTarget) {
        if (target == null || preparedTarget == null) {
            return false;
        }
        ItemMeta preparedMeta = preparedTarget.getItemMeta();
        if (preparedMeta == null) {
            return false;
        }
        return target.setItemMeta(preparedMeta);
    }

    /** 读取当前保底状态，并判断本次是否已达触发条件。 */
    private PityView loadPity(EnhancementRecipe recipe,
            Player player,
            ItemStack target,
            EnhancementTargetProvider provider) {
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
        int counter = state == null ? 0 : state.getCounter();
        Integer threshold = pity.trigger().threshold();
        boolean triggered = threshold != null && counter >= threshold;
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
                case FIXED_DECAY -> Math.max(0, counter - (int) Math.round(decayValue));
                case PROPORTIONAL -> Math.max(0, (int) Math.floor(counter * (1D - decayValue)));
            };
        } else {
            counter = counter + 1;
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
        String recipeId = provider.readRecipeId(player, target);
        return Texts.isBlank(recipeId)
                ? player.getUniqueId().toString()
                : player.getUniqueId() + ":" + recipeId;
    }

    private record MaterialMatch(ItemStack stack, int amount, ConsumeTimingEnum timing) {
    }

    private record PityView(@Nullable PityScopeEnum scope,
            @Nullable String group,
            @Nullable String key,
            int counter,
            boolean triggered) {
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
            EnhancementAttemptPreview preview) {

        static ExecutionPlan invalid(EnhancementRecipe recipe, String errorKey) {
            EnhancementAttemptPreview preview = EnhancementAttemptPreview.rejected(errorKey);
            return new ExecutionPlan(false, recipe, 0, 0, 0, null,
                    new PityView(null, null, null, 0, false), 0D, 0D, false, List.of(), List.of(), preview);
        }
    }
}
