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

final class EnhancementAttemptServiceDelegate {

    private final EnhancementAttemptService ownerRef;

    EnhancementAttemptServiceDelegate(EnhancementAttemptService ownerRef) {
        this.ownerRef = ownerRef;
    }

    boolean accepting() {
        return ownerRef.accepting.get();
    }

    void freezeAccepting() {
        ownerRef.accepting.set(false);
    }

    void resumeAccepting() {
        ownerRef.accepting.set(true);
    }

    boolean drain(long timeout, TimeUnit unit) {
        return ownerRef.operationJournal.drain(timeout, unit);
    }

    @NotNull Map<String, String> journalSnapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        ownerRef.operationJournal.snapshot().forEach((key, entry) -> snapshot.put(key, entry.phase()));
        return Map.copyOf(snapshot);
    }

    @NotNull Map<String, String> journalPhases() {
        return ownerRef.journalSnapshot();
    }

    @Nullable EnhancementOperationView operationView(@Nullable String operationId) {
        if (Texts.isBlank(operationId)) {
            return null;
        }
        for (Map.Entry<String, CraftOperationJournal.Entry<EnhancementAttemptService.JournalEntry>> entry
                : ownerRef.operationJournal.snapshot().entrySet()) {
            if (operationId.equals(EnhancementAttemptServiceSupport.operationIdOf(entry.getKey()))) {
                return ownerRef.toOperationView(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    @NotNull List<EnhancementOperationView> operationViews() {
        List<EnhancementOperationView> views = new ArrayList<>();
        ownerRef.operationJournal.snapshot().forEach((key, entry) -> {
            EnhancementOperationView view = ownerRef.toOperationView(key, entry);
            if (view != null) {
                views.add(view);
            }
        });
        return List.copyOf(views);
    }

    @Nullable EnhancementOperationView toOperationView(String journalKey, CraftOperationJournal.Entry<EnhancementAttemptService.JournalEntry> entry) {
        String operationId = EnhancementAttemptServiceSupport.operationIdOf(journalKey);
        if (Texts.isBlank(operationId)) {
            return null;
        }
        String phase = entry == null ? "" : Texts.toStringSafe(entry.phase());
        EnhancementAttemptService.JournalEntry payload = entry == null ? null : entry.payload();
        EnhancementAttemptResult result = payload == null ? null : payload.result();
        boolean pending = EnhancementAttemptService.ERROR_COMPENSATION_PENDING.equals(phase)
                || EnhancementOperationView.PHASE_COMPENSATION_PENDING.equals(phase)
                || (result != null && EnhancementAttemptService.ERROR_COMPENSATION_PENDING.equals(result.errorKey()));
        return new EnhancementOperationView(operationId,
                entry == null ? null : entry.playerId(), phase, pending);
    }

    @NotNull EnhancementAttemptResult attempt(@Nullable Player player, @Nullable EnhancementRecipe recipe, @Nullable ItemStack target, @Nullable List<ItemStack> supplied) {
        return ownerRef.attempt(player, recipe, target, supplied, UUID.randomUUID().toString());
    }

    @NotNull EnhancementAttemptResult attempt(@Nullable Player player, @Nullable EnhancementRecipe recipe, @Nullable ItemStack target, @Nullable List<ItemStack> supplied, @Nullable String operationId) {
        return ownerRef.attempt(player, recipe, target, supplied, operationId, null);
    }

    @NotNull EnhancementAttemptResult attempt(@Nullable Player player, @Nullable EnhancementRecipe recipe, @Nullable ItemStack target, @Nullable List<ItemStack> supplied, @Nullable String operationId, @Nullable EnhancementTargetVariables.Snapshot expectedSnapshot) {
        return ownerRef.attempt(player, recipe, target, supplied, operationId, expectedSnapshot, null);
    }

    @NotNull EnhancementAttemptResult attemptWithPreview(@Nullable Player player, @Nullable EnhancementRecipe recipe, @Nullable ItemStack target, @Nullable List<ItemStack> supplied, @Nullable String operationId, @Nullable EnhancementPreviewSession previewSession) {
        return ownerRef.attempt(player, recipe, target, supplied, operationId,
                previewSession == null ? null : previewSession.snapshot(),
                previewSession == null ? null : previewSession.plan());
    }

    @NotNull EnhancementAttemptPreview preview(@Nullable Player player, @Nullable EnhancementRecipe recipe, @Nullable ItemStack target, @Nullable List<ItemStack> supplied) {
        return ownerRef.previewSession(player, recipe, target, supplied).preview();
    }

    @NotNull EnhancementPreviewSession previewSession(@Nullable Player player, @Nullable EnhancementRecipe recipe, @Nullable ItemStack target, @Nullable List<ItemStack> supplied) {
        if (player == null || !player.isOnline()) {
            return EnhancementPreviewSession.rejected("strengthen.enhancement.no_player");
        }
        if (recipe == null) {
            return EnhancementPreviewSession.rejected("strengthen.error.no_recipe");
        }
        if (target == null || target.getType().isAir()) {
            return EnhancementPreviewSession.rejected("strengthen.error.no_target");
        }
        EnhancementTargetProvider provider = ownerRef.resolveProvider(player, recipe, target);
        if (provider == null) {
            return EnhancementPreviewSession.rejected("strengthen.enhancement.provider_not_found");
        }
        EnhancementAttemptService.ExecutionPlan plan = ownerRef.prepare(player, recipe, target, supplied == null ? List.of() : supplied, provider);
        return new EnhancementPreviewSession(plan.preview(), plan.snapshot(), plan);
    }

    @Nullable EnhancementAttemptService.ExecutionPlan reusablePlan(Player player, ItemStack target, EnhancementTargetProvider provider, @Nullable EnhancementAttemptService.ExecutionPlan candidate) {
        if (candidate == null || !candidate.valid()) {
            return null;
        }
        EnhancementTargetVariables.Snapshot current =
                EnhancementTargetVariables.capture(player, target, provider);
        EnhancementTargetVariables.Snapshot frozen = candidate.snapshot();
        if (frozen == null || !frozen.sameIdentityAndVersion(current)
                || frozen.level() != current.level() || frozen.temper() != current.temper()) {
            ownerRef.debug(player, "debug.attempt.plan_recomputed", Map.of(
                    "recipe", candidate.recipe() == null ? "-" : candidate.recipe().id(),
                    "reason", "target_state_changed"));
            return null;
        }
        for (EnhancementAttemptService.MaterialMatch match : candidate.materialMatches()) {
            if (match.stack() == null || match.stack().getAmount() < match.amount()) {
                ownerRef.debug(player, "debug.attempt.plan_recomputed", Map.of(
                        "recipe", candidate.recipe() == null ? "-" : candidate.recipe().id(),
                        "reason", "material_amount_changed"));
                return null;
            }
        }
        return candidate;
    }

    @Nullable EnhancementTargetProvider resolveProvider(@Nullable Player player, EnhancementRecipe recipe, ItemStack target) {
        if (ownerRef.targetRegistry == null) {
            return null;
        }
        String providerId = recipe.target().provider();
        EnhancementTargetProvider provider = Texts.isBlank(providerId) ? null : ownerRef.targetRegistry.get(providerId);
        if (provider == null) {
            return null;
        }
        try {
            return provider.canHandle(player, target) ? provider : null;
        } catch (RuntimeException | LinkageError exception) {
            ownerRef.warn("目标 Provider '" + providerId + "' 的 canHandle 抛出异常", exception);
            return null;
        }
    }

    String journalKey(@Nullable Player player, String operationId) {
        return (player == null ? "-" : player.getUniqueId().toString()) + ":" + operationId;
    }

    int attemptFingerprint(@Nullable EnhancementRecipe recipe, @Nullable ItemStack target, @Nullable List<ItemStack> supplied) {
        List<String> materialIdentities = recipe == null ? List.of() : recipe.materials().stream()
                .map(slot -> slot.materialId() + "|" + slot.countKey())
                .toList();
        return Objects.hash(recipe == null ? "" : recipe.id(), materialIdentities, target,
                supplied == null ? List.of() : supplied);
    }

    void completeOperation(String journalKey, int fingerprint, EnhancementAttemptResult result) {
        String phase = result.committed()
                ? (result.success() ? "COMMITTED_SUCCESS" : "COMMITTED_FAILURE")
                : ("strengthen.error.compensation_pending".equals(result.errorKey())
                        ? "COMPENSATION_PENDING" : "NOT_COMMITTED");
        ownerRef.operationJournal.update(journalKey, phase, new EnhancementAttemptService.JournalEntry(fingerprint, result));
        ownerRef.pruneJournal();
    }

    void finishInFlight(String journalKey) {
        ownerRef.operationJournal.release(journalKey);
    }

    void pruneJournal() {
        ownerRef.operationJournal.prune(entry -> {
            EnhancementAttemptResult result = entry.payload() == null ? null : entry.payload().result();
            return result != null && !"strengthen.error.compensation_pending".equals(result.errorKey());
        });
    }

    EnhancementAttemptResult rejectedPlan(EnhancementAttemptService.ExecutionPlan plan, String operation) {
        String errorKey = plan.preview() == null || plan.preview().errorKey().isBlank()
                ? "strengthen.error.rebuild_failed" : plan.preview().errorKey();
        return ownerRef.rejectedWithOperation(errorKey, operation);
    }

    EnhancementAttemptResult rejectedWithOperation(String errorKey, String operation) {
        Map<String, String> placeholders = Texts.isBlank(operation)
                ? Map.of()
                : Map.of("operation_id", operation);
        return EnhancementAttemptResult.rejected(errorKey, placeholders);
    }

    EnhancementAttemptResult compensateAfterCommitFailure(Player player, StrengthenEconomyService economy, List<AttemptCost> appliedCosts, String operation, String fallbackError) {
        if (economy == null) {
            return ownerRef.rejectedWithOperation("strengthen.error.compensation_pending", operation);
        }
        StrengthenEconomyService.RefundResult refund = economy.refundWithResult(player, appliedCosts, operation);
        if (!refund.success()) {
            return ownerRef.rejectedWithOperation("strengthen.error.compensation_pending", operation);
        }
        return ownerRef.rejectedWithOperation(fallbackError, operation);
    }

    void warn(String message, Throwable throwable) {
        if (ownerRef.plugin != null && ownerRef.plugin.getLogger() != null) {
            ownerRef.plugin.getLogger().warning("[attempt] " + message + ": "
                    + (throwable == null ? "未知" : String.valueOf(throwable.getMessage())));
        }
    }

    @NotNull String refreshPreparedPresentation(Player player, ItemStack preparedTarget, EnhancementTargetProvider provider) {
        EmakiResult<Unit> refresh;
        try {
            refresh = provider.refreshPresentation(player, preparedTarget);
        } catch (RuntimeException | LinkageError exception) {
            ownerRef.warn("目标 Provider 刷新阶段抛出异常", exception);
            ownerRef.debug(player, "debug.attempt.refresh_failed", Map.of(
                    "provider", EnhancementAttemptServiceSupport.safeProviderId(provider),
                    "reason", exception.getClass().getSimpleName(),
                    "error_key", EnhancementAttemptService.ERROR_REBUILD_FAILED));
            return EnhancementAttemptService.ERROR_REBUILD_FAILED;
        }
        if (refresh == null) {
            return EnhancementAttemptService.ERROR_REBUILD_FAILED;
        }
        if (refresh.isSuccess()) {
            return "";
        }
        String reasonKey = Texts.isBlank(refresh.reasonKey()) ? EnhancementAttemptService.ERROR_REBUILD_FAILED : refresh.reasonKey();
        ownerRef.warn("目标 Provider 刷新阶段失败，未扣费即拒绝 | 原因键=" + reasonKey, null);
        ownerRef.debug(player, "debug.attempt.refresh_failed", Map.of(
                "provider", EnhancementAttemptServiceSupport.safeProviderId(provider),
                "reason", refresh.failureKind() == null ? "未知" : refresh.failureKind().name(),
                "error_key", reasonKey));
        return reasonKey;
    }

    boolean satisfiesRecipeConditions(EnhancementRecipe recipe, Player player, ItemStack target, VariableContext variables) {
        if (!recipe.conditionsConfigured()) {
            return true;
        }
        Map<String, Object> values = variables == null ? Map.of() : variables.toMap();
        try {
            boolean passed = ConditionEvaluator.evaluate(recipe.conditions(),
                    text -> PlaceholderRenderer.renderPapi(player, text, null, "strengthen_enhancement"),
                    ConditionContext.of(player, target, values));
            if (!passed) {
                ownerRef.debug(player, "debug.attempt.condition_rejected", Map.of(
                        "recipe", recipe.id(),
                        "condition_type", recipe.conditions().conditionType(),
                        "required", recipe.conditions().requiredCount()));
            }
            return passed;
        } catch (RuntimeException | LinkageError exception) {
            ownerRef.warn("配方条件求值失败，按不通过处理", exception);
            ownerRef.debug(player, "debug.attempt.condition_rejected", Map.of(
                    "recipe", recipe.id(),
                    "condition_type", exception.getClass().getSimpleName(),
                    "required", 0));
            return false;
        }
    }

    boolean sameFrozenTarget(@Nullable EnhancementTargetVariables.Snapshot expected, @Nullable EnhancementTargetVariables.Snapshot actual, Player player, String operation) {
        if (expected == null || actual == null) {
            return true;
        }
        if (expected.sameIdentityAndVersion(actual)) {
            return true;
        }
        ownerRef.warn("目标在预览与确认之间发生变化，已在扣费前拒绝 | 操作=" + operation, null);
        ownerRef.debug(player, "debug.attempt.target_changed", Map.of(
                "operation_id", operation,
                "expected_version", expected.version(),
                "actual_version", actual.version(),
                "expected_instance", expected.instanceId(),
                "actual_instance", actual.instanceId()));
        return false;
    }

    boolean containerBackedTarget(Player player, ItemStack target) {
        if (ownerRef.plugin == null || ownerRef.plugin.appConfig() == null
                || !ownerRef.plugin.appConfig().enhancementRejectContainerTarget()) {
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

    void logContainerRejection(Player player, ItemStack target) {
        ownerRef.warn("强化目标位于外部容器，已拒绝执行", null);
        ownerRef.debug(player, "debug.attempt.container_rejected", Map.of(
                "item", target == null ? "-" : target.getType().name(),
                "error_key", EnhancementAttemptService.ERROR_CONTAINER_TARGET));
    }

    void reportUnreadablePdc(Player player, EnhancementTargetVariables.Snapshot snapshot) {
        if (snapshot.unreadablePdcCount() <= 0) {
            return;
        }
        ownerRef.debug(player, "debug.attempt.pdc_unreadable", Map.of(
                "count", snapshot.unreadablePdcCount(),
                "keys", String.join(",", snapshot.unreadablePdcKeys()),
                "provider", snapshot.providerId()));
    }

    VariableContext buildVariablesFromSnapshot(Player player, EnhancementTargetVariables.Snapshot snapshot, int evaluationLevel) {
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

    boolean testMatcher(MaterialSlotConfig slot, ItemStack candidate, ItemStack target, @Nullable ItemSourceRef targetSource, Player player, VariableContext variables, int targetLevel, EnhancementTargetProvider provider, Map<ItemStack, VariableContext> comparisonVariables) {
        try {
            VariableContext effective = variables;
            if (slot.comparesTarget()) {
                effective = comparisonVariables.computeIfAbsent(candidate,
                        stack -> EnhancementMaterialVariables.enrich(variables, stack, target, targetLevel,
                                provider, ownerRef.affixLayerCodec));
                if (!EnhancementAttemptServiceSupport.satisfiesTargetCompare(slot.targetCompare(), effective)) {
                    return false;
                }
            }
            MatchContext context = new MatchContext(candidate, identifySource(candidate), player,
                    target, targetSource, effective);
            return slot.requirement().test(context);
        } catch (RuntimeException | LinkageError exception) {
            ownerRef.warn("材料判定抛出异常，视为不匹配", exception);
            return false;
        }
    }

    boolean matchesTargetFilter(EnhancementRecipe recipe, @Nullable Player player, ItemStack target, VariableContext variables) {
        Matcher filterMatcher = recipe.target().filterMatcher();
        if (filterMatcher != null) {
            return ownerRef.testTargetFilterMatcher(filterMatcher, player, target, variables);
        }
        Map<String, Object> filter = recipe.target().filter();
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        Map<String, Object> values = variables == null ? Map.of() : variables.toMap();
        ItemSourceRef source = ownerRef.identifySource(target);
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
            if (!EnhancementAttemptServiceSupport.sameFilterValue(actual, expected)) {
                return false;
            }
        }
        return true;
    }

    boolean testTargetFilterMatcher(Matcher filterMatcher, @Nullable Player player, ItemStack target, VariableContext variables) {
        try {
            VariableContext effective = variables == null
                    ? VariableContext.builder(player).build()
                    : variables;
            MatchContext context = new MatchContext(target, identifySource(target), player,
                    target, identifySource(target), effective);
            return filterMatcher.test(context);
        } catch (RuntimeException | LinkageError exception) {
            ownerRef.warn("目标 Matcher 判定抛出异常，视为不匹配", exception);
            return false;
        }
    }

    boolean ensurePityOwner(ItemStack target) {
        if (target == null || target.getType().isAir()) {
            return false;
        }
        ItemMeta meta = target.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (!meta.getPersistentDataContainer().has(EnhancementAttemptService.PITY_OWNER_KEY, PersistentDataType.STRING)) {
            meta.getPersistentDataContainer().set(EnhancementAttemptService.PITY_OWNER_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            if (!target.setItemMeta(meta)) {
                return false;
            }
        }
        return target.getItemMeta() != null
                && target.getItemMeta().getPersistentDataContainer().has(EnhancementAttemptService.PITY_OWNER_KEY, PersistentDataType.STRING);
    }

    @Nullable ItemSourceRef identifySource(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || ownerRef.plugin == null) {
            return null;
        }
        try {
            ItemSourceService itemSourceService = ownerRef.plugin.coreItemSourceService();
            return itemSourceService == null ? null : itemSourceService.identifyItem(itemStack);
        } catch (RuntimeException | LinkageError exception) {
            ownerRef.warn("识别材料物品源失败", exception);
            return null;
        }
    }

    List<EnhancementAttemptPreview.MaterialRequirement> materialRequirements( EnhancementRecipe recipe, Player player, ItemStack target, List<ItemStack> supplied, VariableContext variables, List<Integer> requiredAmounts, int targetLevel, EnhancementTargetProvider provider) {
        List<EnhancementAttemptPreview.MaterialRequirement> result = new ArrayList<>();
        ItemSourceRef targetSource = ownerRef.identifySource(target);
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
                            && ownerRef.testMatcher(slot, candidate, target, targetSource, player, variables,
                                    targetLevel, provider, comparisonVariables)) {
                        available = EnhancementAttemptServiceSupport.saturatedAdd(available, candidate.getAmount());
                    }
                }
            }
            result.add(new EnhancementAttemptPreview.MaterialRequirement(
                    slot.materialId(), required, available, slot.consumeTiming()));
        }
        return List.copyOf(result);
    }

    boolean containerBackedMaterials(Player player, List<ItemStack> supplied) {
        if (supplied.isEmpty() || ownerRef.plugin == null || ownerRef.plugin.appConfig() == null
                || !ownerRef.plugin.appConfig().enhancementRejectContainerMaterial()) {
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

    void logContainerMaterialRejection(Player player, int suppliedCount) {
        ownerRef.warn("强化材料位于外部容器，已拒绝执行", null);
        ownerRef.debug(player, "debug.attempt.container_material_rejected", Map.of(
                "count", suppliedCount,
                "error_key", EnhancementAttemptService.ERROR_CONTAINER_MATERIAL));
    }

    List<AttemptCost> applyCostGrowth(List<AttemptCost> costs, int currentLevel) {
        double perLevel = ownerRef.plugin == null || ownerRef.plugin.appConfig() == null
                ? 0D : ownerRef.plugin.appConfig().enhancementCostGrowthPerLevel();
        if (costs.isEmpty() || perLevel <= 0D || currentLevel <= 0) {
            return costs;
        }
        double multiplier = 1D + perLevel * currentLevel;
        double cap = ownerRef.plugin.appConfig().enhancementCostGrowthMaxMultiplier();
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

    double applyDiminishingReturns(double baseRate, int currentLevel) {
        double perLevel = ownerRef.plugin == null || ownerRef.plugin.appConfig() == null
                ? 0D : ownerRef.plugin.appConfig().enhancementDiminishingPerLevel();
        if (perLevel <= 0D || currentLevel <= 0) {
            return baseRate;
        }
        int startLevel = ownerRef.plugin.appConfig().enhancementDiminishingStartLevel();
        if (currentLevel < startLevel) {
            return baseRate;
        }
        double factor = 1D - perLevel * (currentLevel - startLevel + 1);
        return EnhancementAttemptServiceSupport.clampChance(baseRate * Math.max(0D, factor));
    }

    double applyMinimumSuccessRate(double rate) {
        double floor = ownerRef.plugin == null || ownerRef.plugin.appConfig() == null
                ? 0D : ownerRef.plugin.appConfig().enhancementMinSuccessRate();
        if (floor <= 0D) {
            return rate;
        }
        return EnhancementAttemptServiceSupport.clampChance(Math.max(rate, floor));
    }

    int resolveFailureLevel(int currentLevel) {
        int demotion = ownerRef.plugin == null || ownerRef.plugin.appConfig() == null
                ? 0 : ownerRef.plugin.appConfig().enhancementFailureDemotionLevels();
        if (demotion <= 0 || currentLevel <= 0) {
            return currentLevel;
        }
        int floor = Math.max(0, ownerRef.plugin.appConfig().enhancementFailureDemotionFloor());
        if (currentLevel <= floor) {
            return currentLevel;
        }
        return Math.max(floor, currentLevel - demotion);
    }

    StrengthenEconomyService.ChargeResult chargeCurrencies(Player player, List<AttemptCost> costs, StrengthenEconomyService economy, String operation) {
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
            ownerRef.warn("强化框架扣费失败", exception);
            return StrengthenEconomyService.ChargeResult.failure(
                    "strengthen.error.economy_provider_unavailable", List.of());
        }
    }

    Map<ItemStack, Integer> snapshotAmounts(List<EnhancementAttemptService.MaterialMatch> matches) {
        Map<ItemStack, Integer> snapshot = new IdentityHashMap<>();
        if (matches != null) {
            for (EnhancementAttemptService.MaterialMatch match : matches) {
                if (match != null && match.stack() != null) {
                    snapshot.putIfAbsent(match.stack(), match.stack().getAmount());
                }
            }
        }
        return snapshot;
    }

    void restoreAmounts(Map<ItemStack, Integer> snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshot.forEach((stack, amount) -> {
            if (stack != null) {
                stack.setAmount(Math.max(0, amount));
            }
        });
    }

    void consumeMaterials(List<EnhancementAttemptService.MaterialMatch> matches, boolean success) {
        for (EnhancementAttemptService.MaterialMatch match : matches) {
            if (!EnhancementAttemptServiceSupport.shouldConsume(match.timing(), success)) {
                continue;
            }
            ItemStack stack = match.stack();
            int remaining = stack.getAmount() - match.amount();
            stack.setAmount(Math.max(0, remaining));
        }
    }

    boolean commitPreparedTarget(Player player, ItemStack target, ItemStack preparedTarget, ItemStack originalTarget, EnhancementTargetProvider provider, int expectedLevel) {
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
            ownerRef.warn("目标 Provider 提交后回读失败", exception);
        }
        ItemMeta originalMeta = originalTarget.getItemMeta();
        if (originalMeta != null && !target.setItemMeta(originalMeta)) {
            ownerRef.warn("目标 Provider 回读失败后的目标回滚失败", null);
        }
        return false;
    }

}
