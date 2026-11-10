package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.action.CoreActionItemTarget;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.corelib.craft.CraftOperationJournal;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.CraftRollEngine;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.event.StrengthenAttemptEvent;
import emaki.jiuwu.craft.strengthen.api.event.StrengthenPreAttemptEvent;
import emaki.jiuwu.craft.strengthen.api.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.api.model.AttemptMaterial;
import emaki.jiuwu.craft.strengthen.api.model.AttemptOutcome;
import emaki.jiuwu.craft.strengthen.api.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.api.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenBranchNode;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenConditionGroup;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenConditionNode;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;
import emaki.jiuwu.craft.corelib.condition.ConditionNode;

public final class StrengthenAttemptService {

    private static final String PDC_ATTRIBUTE_SOURCE_ID = "strengthen";
    static final String OPERATION_NAMESPACE = "strengthen";
    private static final int MAX_JOURNAL_ENTRIES = 4_096;

    final EmakiStrengthenPlugin plugin;
    private final StrengthenRecipeResolver recipeResolver;
    private final MaterialPlanResolver materialPlanResolver;
    private final ChanceCalculator chanceCalculator;
    private final StrengthenEconomyService economyService;
    private final StrengthenSnapshotBuilder snapshotBuilder;
    final StrengthenActionCoordinator actionCoordinator;
    private final EmakiItemAssemblyService itemAssemblyService;
    final StrengthenPdcAttributeWriter pdcAttributeWriter;
    final ItemOperationLedger operationLedger;
    final ThreadOwnership threadOwnership;
    final AtomicBoolean accepting = new AtomicBoolean(true);
    final CraftOperationJournal<JournalEntry> operationJournal =
            CraftOperationJournal.ofMemory(MAX_JOURNAL_ENTRIES);

    public StrengthenAttemptService(EmakiStrengthenPlugin plugin,
            StrengthenRecipeResolver recipeResolver,
            ChanceCalculator chanceCalculator,
            StrengthenEconomyService economyService,
            StrengthenSnapshotBuilder snapshotBuilder,
            StrengthenActionCoordinator actionCoordinator,
            EmakiItemAssemblyService itemAssemblyService,
            ThreadOwnership threadOwnership,
            PlaceholderRegistry placeholderRegistry) {
        this.plugin = plugin;
        this.recipeResolver = recipeResolver;
        this.materialPlanResolver = new MaterialPlanResolver(recipeResolver, plugin);
        this.chanceCalculator = chanceCalculator;
        this.economyService = economyService;
        this.snapshotBuilder = snapshotBuilder;
        this.actionCoordinator = actionCoordinator;
        this.itemAssemblyService = itemAssemblyService;
        this.threadOwnership = threadOwnership;
        this.pdcAttributeWriter = new StrengthenPdcAttributeWriter(plugin, PDC_ATTRIBUTE_SOURCE_ID);
        this.operationLedger = new ItemOperationLedger(plugin::debugLogger, placeholderRegistry);
    }

    public boolean canStrengthen(ItemStack itemStack) { return new StrengthenAttemptServiceDelegate(this).canStrengthen(itemStack); }

    public StrengthenState readState(ItemStack itemStack) { return new StrengthenAttemptServiceDelegate(this).readState(itemStack); }

    ResolvedState resolveState(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return new ResolvedState(StrengthenState.ineligible("strengthen.error.no_target", null, ""), StoredState.empty(null, ""));
        }
        ItemSourceRef initialBaseSource = recipeResolver.resolveBaseSource(itemStack);
        String initialSignature = ItemSourceUtil.toShorthand(initialBaseSource);
        StoredState stored = readStoredState(itemStack, initialBaseSource, initialSignature);
        StrengthenRecipeResolver.ResolvedItem resolved = recipeResolver.resolve(itemStack, stored.recipeId());
        if (resolved.baseSource() == null) {
            return new ResolvedState(StrengthenState.ineligible("strengthen.error.no_source", null, ""), stored);
        }
        stored = stored.withBaseSourceSignature(resolved.baseSourceSignature());
        String recipeId = Texts.isNotBlank(stored.recipeId()) ? stored.recipeId() : resolved.recipeId();
        boolean eligible = Texts.isNotBlank(recipeId) && plugin.recipeLoader().get(recipeId) != null;
        String reason = eligible ? "" : "strengthen.error.no_recipe";
        return new ResolvedState(
                new StrengthenState(
                        eligible,
                        reason,
                        stored.hasLayer(),
                        resolved.baseSourceSignature(),
                        resolved.baseSourceSignature(),
                        recipeId,
                        stored.currentStar(),
                        stored.crackLevel(),
                        stored.firstReachFlags(),
                        stored.successCount(),
                        stored.failureCount(),
                        stored.lastAttemptAt(),
                        stored.branchPath()
                ),
                stored
        );
    }

    public AttemptPreview preview(Player player, AttemptContext context) {
        ItemStack targetItem = context == null ? null : context.targetItem();
        StrengthenState state = readState(targetItem);
        if (!state.eligible()) {
            return ineligiblePreview(state.eligibleReason(), state);
        }
        StrengthenRecipe recipe = plugin.recipeLoader().get(state.recipeId());
        if (recipe == null) {
            return ineligiblePreview("strengthen.error.no_recipe", state);
        }
        if (state.currentStar() >= recipe.limits().maxStar()) {
            return ineligiblePreview("strengthen.error.already_max", state, recipe);
        }

        int targetStar = state.currentStar() + 1;
        if (recipe.hasBranchTree()
                && recipe.branchTree().needsForkSelection(state.branchPath(), state.currentStar())) {
            return ineligiblePreview("strengthen.branch.selection_required", state, recipe);
        }
        StrengthenRecipe.StarStage stage = recipe.stage(targetStar, state.branchPath());
        if (stage == null) {
            return ineligiblePreview("strengthen.error.already_max", state, recipe);
        }

        MaterialPlanResolver.MaterialPlan materials = materialPlanResolver.resolveMaterialPlan(
                context, stage, player, recipe.id(), state.branchPath());
        if (Texts.isNotBlank(materials.errorKey())) {
            return new AttemptPreview(false, materials.errorKey(), state, recipe, state.currentStar(), targetStar, 0D, List.of(),
                    state.currentStar(), state.temperLevel(), false, 0, Map.of(), Set.of(), materials.requiredMaterials(), materials.optionalMaterials());
        }

        double successRate = chanceCalculator.calculateSuccessRate(plugin.appConfig(), recipe, state.currentStar(), state.temperLevel(),
                materials.appliedTemperBonus());
        ChanceCalculator.FailureResolution failure = chanceCalculator.resolveFailure(recipe, state.currentStar(), state.temperLevel(),
                materials.appliedTemperBonus(), materials.protectionApplied());
        Set<Integer> firstReachStars = collectFirstReach(state.firstReachFlags(), targetStar);
        AttemptPreview preview = new AttemptPreview(
                true,
                "",
                state,
                recipe,
                state.currentStar(),
                targetStar,
                successRate,
                economyService.quoteCosts(recipe, targetStar),
                failure.resultingStar(),
                failure.resultingTemper(),
                materials.protectionApplied(),
                materials.appliedTemperBonus(),
                recipe.deltaStats(state.currentStar(), targetStar),
                firstReachStars,
                materials.requiredMaterials(),
                materials.optionalMaterials()
        );
        return preview;
    }

    public AttemptResult attempt(Player player, AttemptContext context) {
        String operationId = resolveOperationId(context);
        AttemptContext safeContext = context == null
                ? AttemptContext.of(null, List.of(), operationId)
                : context.withOperationId(operationId);
        String journalKey = journalKey(player, operationId);
        int fingerprint = attemptFingerprint(safeContext);
        AttemptStart start = beginOperation(journalKey, player == null ? null : player.getUniqueId(), fingerprint);
        if (start.existingResult() != null) {
            return start.existingResult();
        }
        if (!start.started()) {
            AttemptPreview rejectedPreview = preview(player, safeContext);
            return AttemptResult.failure(start.errorKey(), rejectedPreview,
                    replacements(rejectedPreview, rejectedPreview.currentStar()), operationId);
        }

        AttemptResult result = null;
        AttemptPreview preview = null;
        try {
            logOperation(player, operationId, "started", AttemptOutcome.NOT_COMMITTED);
            preview = preview(player, safeContext);
            result = attemptOnce(player, safeContext, preview, operationId);
        } catch (RuntimeException | LinkageError exception) {
            plugin.messageService().severe("console.strengthen_attempt_failed_closed", Map.of(
                    "operation_id", Texts.toStringSafe(operationId),
                    "error", String.valueOf(exception.getMessage())));
            result = internalFailure(player, safeContext, preview, operationId);
        } finally {
            if (result == null) {
                result = AttemptResult.failure("strengthen.error.internal", null, Map.of(), operationId);
            }
            completeOperation(journalKey, fingerprint, result);
            finishInFlight(journalKey);
            logOperation(player, operationId, "completed", result.outcome());
        }
        return result;
    }

    private AttemptResult internalFailure(Player player,
            AttemptContext context,
            AttemptPreview resolvedPreview,
            String operationId) { return new StrengthenAttemptServiceDelegate(this).internalFailure(player, context, resolvedPreview, operationId); }

    private AttemptResult attemptOnce(Player player,
            AttemptContext context,
            AttemptPreview preview,
            String operationId) {
        if (!preview.eligible()) {
            return finishAttempt(player, AttemptResult.failure(preview.errorKey(), preview,
                    replacements(preview, preview.currentStar()), operationId));
        }

        StrengthenRecipe recipe = preview.recipe();
        if (recipe != null && !recipe.conditions().emptyGroup()) {
            boolean conditionsPassed = ConditionEvaluator.evaluate(
                    toCoreConditionGroup(recipe.conditions()),
                    text -> PlaceholderRenderer.renderPapi(player, text, null, "strengthen_attempt"),
                    true,
                    ConditionContext.of(player, context.targetItem(),
                            Map.of(
                                    "operationId", operationId,
                                    "recipeId", recipe.id(),
                                    "currentStar", preview.currentStar(),
                                    "targetStar", preview.targetStar(),
                                    "successRate", preview.successRate()))
            );
            if (!conditionsPassed) {
                return finishAttempt(player, AttemptResult.failure("strengthen.error.condition_not_met", preview,
                        replacements(preview, preview.currentStar()), operationId));
            }
        }

        double rollSuccessRate = CraftRollEngine.clamp(preview.successRate());
        if (isPlayerOwned(player)) {
            StrengthenPreAttemptEvent preAttemptEvent = new StrengthenPreAttemptEvent(
                    player,
                    context.targetItem(),
                    recipe == null ? null : recipe.id(),
                    preview.currentStar(),
                    preview.targetStar(),
                    rollSuccessRate,
                    operationId);
            Bukkit.getPluginManager().callEvent(preAttemptEvent);
            if (preAttemptEvent.isCancelled()) {
                return finishAttempt(player, AttemptResult.failure("strengthen.error.cancelled", preview,
                        replacements(preview, preview.currentStar()), operationId));
            }
            rollSuccessRate = CraftRollEngine.clamp(preAttemptEvent.getSuccessRate());
        }

        boolean success = CraftRollEngine.roll(rollSuccessRate);
        StrengthenState currentState = preview.state();
        int resultStar = success ? preview.targetStar() : preview.failureStar();
        int resultTemper = success ? 0 : preview.failureTemper();
        StarProgress progress = collectStarProgress(currentState.firstReachFlags(), success ? Set.of(preview.targetStar()) : Set.of());

        StrengthenState updated = new StrengthenState(
                true,
                "",
                true,
                currentState.baseSource(),
                currentState.baseSourceSignature(),
                preview.recipe().id(),
                resultStar,
                resultTemper,
                progress.updatedFlags(),
                currentState.successCount() + (success ? 1 : 0),
                currentState.failureCount() + (success ? 0 : 1),
                System.currentTimeMillis(),
                currentState.branchPath()
        );

        ItemStack rebuilt = rebuildWithState(context.targetItem(), updated, buildMaterialsSignature(preview),
                player == null ? null : ActionContext.create(player, "attempt", true));
        if (rebuilt == null) {
            return finishAttempt(player, AttemptResult.failure("strengthen.error.rebuild_failed", preview,
                    replacements(preview, resultStar), operationId));
        }

        StrengthenEconomyService.ChargeResult chargeResult = economyService.charge(player, preview.costs(), operationId);
        if (!chargeResult.success()) {
            AttemptOutcome outcome = chargeResult.compensationPending()
                    ? AttemptOutcome.COMPENSATION_PENDING : AttemptOutcome.NOT_COMMITTED;
            return finishAttempt(player, AttemptResult.failure(chargeResult.errorKey(), preview,
                    replacements(preview, preview.currentStar()), operationId, outcome));
        }

        AttemptOutcome outcome = success ? AttemptOutcome.COMMITTED_SUCCESS : AttemptOutcome.COMMITTED_FAILURE;
        return finishAttempt(player, new AttemptResult(success, "", replacements(preview, resultStar), preview,
                rebuilt, resultStar, resultTemper, progress.newlyReached(), operationId, outcome));
    }

    private AttemptResult finishAttempt(Player player, AttemptResult result) { return new StrengthenAttemptServiceDelegate(this).finishAttempt(player, result); }

    boolean isPlayerOwned(Player player) { return new StrengthenAttemptServiceDelegate(this).isPlayerOwned(player); }

    public boolean accepting() { return new StrengthenAttemptServiceDelegate(this).accepting(); }

    public void freezeAccepting() { new StrengthenAttemptServiceDelegate(this).freezeAccepting(); }

    public void resumeAccepting() { new StrengthenAttemptServiceDelegate(this).resumeAccepting(); }

    public boolean drain(long timeout, TimeUnit unit) { return new StrengthenAttemptServiceDelegate(this).drain(timeout, unit); }

    public Map<String, String> journalSnapshot() { return new StrengthenAttemptServiceDelegate(this).journalSnapshot(); }

    AttemptStart beginOperation(String journalKey, UUID playerId, int fingerprint) {
        CraftOperationJournal.Entry<JournalEntry> existing = operationJournal.beginIfAbsent(
                journalKey, OPERATION_NAMESPACE, playerId, new JournalEntry(fingerprint, null));
        if (existing != null) {
            JournalEntry payload = existing.payload();
            if (payload.fingerprint() != fingerprint) {
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

    private void completeOperation(String journalKey, int fingerprint, AttemptResult result) {
        operationJournal.update(journalKey, result.outcome().name(), new JournalEntry(fingerprint, result));
        pruneJournal();
    }

    private void finishInFlight(String journalKey) { new StrengthenAttemptServiceDelegate(this).finishInFlight(journalKey); }

    private void pruneJournal() { new StrengthenAttemptServiceDelegate(this).pruneJournal(); }

    private String resolveOperationId(AttemptContext context) { return new StrengthenAttemptServiceDelegate(this).resolveOperationId(context); }

    private String journalKey(Player player, String operationId) { return new StrengthenAttemptServiceDelegate(this).journalKey(player, operationId); }

    private int attemptFingerprint(AttemptContext context) { return new StrengthenAttemptServiceDelegate(this).attemptFingerprint(context); }

    private void logOperation(Player player, String operationId, String phase, AttemptOutcome outcome) {
        UUID playerId = player == null ? null : player.getUniqueId();
        if (plugin.debugLogger() != null && plugin.debugLogger().shouldLog("attempt", playerId)) {
            plugin.debugLogger().log("attempt", playerId, "attempt.operation", Map.of(
                    "operation_id", Texts.toStringSafe(operationId),
                    "phase", Texts.toStringSafe(phase),
                    "outcome", outcome == null ? "" : outcome.name()
            ));
        }
    }

    public ItemStack rebuild(ItemStack itemStack) {
        return rebuild(null, itemStack);
    }

    public ItemStack rebuild(Player player, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return itemStack;
        }
        ResolvedState resolvedState = resolveState(itemStack);
        StrengthenState state = resolvedState.state();
        if (!state.hasLayer() || Texts.isBlank(state.recipeId())) {
            return itemStack;
        }
        return rebuildWithState(itemStack, state, resolvedState.stored().materialsSignature(),
                player == null ? null : ActionContext.create(player, "rebuild", true));
    }

    public ItemStack applyAdminState(ItemStack itemStack, Integer star, Integer temper, String recipeId) {
        return applyAdminState(null, itemStack, star, temper, recipeId);
    }

    public ItemStack applyAdminState(Player player, ItemStack itemStack, Integer star, Integer temper, String recipeId) {
        StrengthenState current = readState(itemStack);
        if (Texts.isBlank(current.baseSource())) {
            return null;
        }
        String effectiveRecipe = Texts.isNotBlank(recipeId) ? recipeId : current.recipeId();
        StrengthenRecipe recipe = plugin.recipeLoader().get(effectiveRecipe);
        if (recipe == null) {
            return null;
        }
        StrengthenState updated = new StrengthenState(
                true,
                "",
                true,
                current.baseSource(),
                current.baseSourceSignature(),
                effectiveRecipe,
                star == null ? current.currentStar() : Numbers.clamp(star, 0, recipe.limits().maxStar()),
                temper == null ? current.temperLevel() : Numbers.clamp(temper, 0, recipe.limits().maxTemper()),
                current.milestoneFlags(),
                current.successCount(),
                current.failureCount(),
                System.currentTimeMillis(),
                current.branchPath()
        );
        return rebuildWithState(itemStack, updated,
                readStoredState(itemStack, ItemSourceUtil.parse(current.baseSource()), current.baseSourceSignature()).materialsSignature(),
                player == null ? null : ActionContext.create(player, "admin", true));
    }

    public BranchSelection selectBranch(ItemStack itemStack, String childId) {
        return selectBranch(null, itemStack, childId);
    }

    public BranchSelection selectBranch(Player player, ItemStack itemStack, String childId) {
        StrengthenState current = readState(itemStack);
        if (!current.eligible()) {
            return BranchSelection.failure(Texts.isBlank(current.eligibleReason())
                    ? "strengthen.error.no_recipe"
                    : current.eligibleReason());
        }
        StrengthenRecipe recipe = plugin.recipeLoader().get(current.recipeId());
        if (recipe == null) {
            return BranchSelection.failure("strengthen.error.no_recipe");
        }
        if (!recipe.hasBranchTree()) {
            return BranchSelection.failure("strengthen.branch.not_branching");
        }
        StrengthenBranchNode tree = recipe.branchTree();
        if (!tree.needsForkSelection(current.branchPath(), current.currentStar())) {
            return BranchSelection.failure("strengthen.branch.not_pending");
        }
        Map<String, StrengthenBranchNode> options = tree.childrenAt(current.branchPath());
        if (options.isEmpty()) {
            return BranchSelection.failure("strengthen.branch.not_pending");
        }
        if (Texts.isBlank(childId)) {
            return BranchSelection.pending(options);
        }
        String resolvedId = null;
        for (String candidate : options.keySet()) {
            if (candidate.equalsIgnoreCase(childId)) {
                resolvedId = candidate;
            }
        }
        if (resolvedId == null) {
            return BranchSelection.invalid(options);
        }
        String updatedPath = StrengthenBranchNode.appendBranch(current.branchPath(), resolvedId);
        StrengthenState updated = new StrengthenState(
                true,
                "",
                true,
                current.baseSource(),
                current.baseSourceSignature(),
                current.recipeId(),
                current.currentStar(),
                current.temperLevel(),
                current.milestoneFlags(),
                current.successCount(),
                current.failureCount(),
                current.lastAttemptAt(),
                updatedPath
        );
        ItemStack rebuilt = rebuildWithState(itemStack, updated,
                readStoredState(itemStack, ItemSourceUtil.parse(current.baseSource()),
                        current.baseSourceSignature()).materialsSignature(),
                player == null ? null : ActionContext.create(player, "branch", true));
        if (rebuilt == null) {
            return BranchSelection.failure("strengthen.branch.apply_failed");
        }
        return BranchSelection.applied(rebuilt, resolvedId, options.get(resolvedId), updatedPath);
    }

    public ItemStack clearStrengthenLayer(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        if (itemAssemblyService == null || !itemAssemblyService.isEmakiItem(itemStack)) {
            return null;
        }
        if (itemAssemblyService.readLayerSnapshot(itemStack, "strengthen") == null) {
            return null;
        }
        ItemStack rebuilt = itemAssemblyService.removeLayer(itemStack, "strengthen");
        if (rebuilt == null) {
            return null;
        }
        rebuilt.setAmount(Math.max(1, itemStack.getAmount()));
        preserveOtherAttributePayloads(itemStack, rebuilt);
        clearPdcAttributes(rebuilt);
        return rebuilt;
    }

    public CompletableFuture<Boolean> triggerSuccessActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            CoreActionItemTarget itemTarget,
            int star,
            int temper) { return new StrengthenAttemptServiceDelegate(this).triggerSuccessActions(player, recipe, resultSlotId, itemTarget, star, temper); }

    public CompletableFuture<Boolean> triggerSuccessActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            CoreActionItemTarget itemTarget,
            int star,
            int temper,
            String operationId) { return new StrengthenAttemptServiceDelegate(this).triggerSuccessActions(player, recipe, resultSlotId, itemTarget, star, temper, operationId); }

    public CompletableFuture<Boolean> triggerFailureActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            CoreActionItemTarget itemTarget,
            int wasStar,
            int resultStar,
            int temper,
            boolean dropped,
            boolean protectionApplied) { return new StrengthenAttemptServiceDelegate(this).triggerFailureActions(player, recipe, resultSlotId, itemTarget, wasStar, resultStar, temper, dropped, protectionApplied); }

    public CompletableFuture<Boolean> triggerFailureActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            CoreActionItemTarget itemTarget,
            int wasStar,
            int resultStar,
            int temper,
            boolean dropped,
            boolean protectionApplied,
            String operationId) { return new StrengthenAttemptServiceDelegate(this).triggerFailureActions(player, recipe, resultSlotId, itemTarget, wasStar, resultStar, temper, dropped, protectionApplied, operationId); }

    public void broadcastFirstReach(Player player, ItemStack resultItem, Set<Integer> newlyReached) {
        if (player == null || newlyReached == null || newlyReached.isEmpty()) {
            return;
        }
        String showItem = actionCoordinator.buildShowItem(resultItem);
        for (int star : plugin.appConfig().localBroadcastStars()) {
            if (!newlyReached.contains(star)) {
                continue;
            }
            String message = plugin.messageService().message("strengthen.broadcast.local_reach", Map.of(
                    "player", player.getName(),
                    "show_item", showItem,
                    "star", star
            ));
            double radius = plugin.appConfig().localBroadcastRadius();
            double radiusSquared = radius * radius;
            var world = player.getWorld();
            var playerLocation = player.getLocation();
            world.getPlayers().stream()
                    .filter(viewer -> viewer.getLocation().distanceSquared(playerLocation) <= radiusSquared)
                    .forEach(viewer -> plugin.messageService().sendRaw(viewer, message));
        }
        for (int star : plugin.appConfig().globalBroadcastStars()) {
            if (!newlyReached.contains(star)) {
                continue;
            }
            String message = plugin.messageService().message("strengthen.broadcast.global_reach", Map.of(
                    "player", player.getName(),
                    "show_item", showItem,
                    "star", star
            ));
            Bukkit.getOnlinePlayers().forEach(viewer -> plugin.messageService().sendRaw(viewer, message));
        }
    }

    AttemptPreview ineligiblePreview(String errorKey, StrengthenState state) { return new StrengthenAttemptServiceDelegate(this).ineligiblePreview(errorKey, state); }

    AttemptPreview ineligiblePreview(String errorKey, StrengthenState state, StrengthenRecipe recipe) { return new StrengthenAttemptServiceDelegate(this).ineligiblePreview(errorKey, state, recipe); }

    StoredState readStoredState(ItemStack itemStack, ItemSourceRef baseSource, String fallbackSignature) {
        if (itemAssemblyService == null || itemStack == null || !itemAssemblyService.isEmakiItem(itemStack)) {
            return StoredState.empty(baseSource, fallbackSignature);
        }
        EmakiItemLayerSnapshot snapshot = itemAssemblyService.readLayerSnapshot(itemStack, "strengthen");
        if (snapshot == null) {
            return StoredState.empty(baseSource, fallbackSignature);
        }
        Map<String, Object> audit = snapshot.audit();
        return new StoredState(
                true,
                Texts.toStringSafe(audit.get("recipe_id")),
                Numbers.tryParseInt(audit.get("current_star"), 0),
                Numbers.tryParseInt(audit.get("crack_level"), 0),
                parseFlagSet(audit.get("first_reach_flags")),
                Numbers.tryParseInt(audit.get("success_count"), 0),
                Numbers.tryParseInt(audit.get("failure_count"), 0),
                Numbers.tryParseLong(audit.get("last_attempt_at"), 0L),
                Texts.toStringSafe(audit.get("materials_signature")),
                Texts.isBlank(Texts.toStringSafe(audit.get("base_source_signature")))
                        ? fallbackSignature
                        : Texts.toStringSafe(audit.get("base_source_signature")),
                Texts.toStringSafe(audit.get("branch_path"))
        );
    }

    private Set<Integer> parseFlagSet(Object raw) { return new StrengthenAttemptServiceDelegate(this).parseFlagSet(raw); }

    private ItemStack rebuildWithState(ItemStack itemStack, StrengthenState state, String materialsSignature,
            ActionContext actionContext) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        StrengthenRecipe recipe = plugin.recipeLoader().get(state.recipeId());
        if (recipe == null) {
            return null;
        }
        if (itemAssemblyService == null) {
            return null;
        }
        EmakiItemLayerSnapshot snapshot = snapshotBuilder.buildLayerSnapshot(recipe, state, materialsSignature);
        ItemStack rebuilt = itemAssemblyService.preview(new EmakiItemAssemblyRequest(
                ItemSourceUtil.parse(state.baseSource()),
                Math.max(1, itemStack.getAmount()),
                itemStack,
                List.of(snapshot)
        ));
        if (rebuilt != null) {
            rebuilt.setAmount(Math.max(1, itemStack.getAmount()));
            pdcAttributeWriter.preserveOtherAttributePayloads(itemStack, rebuilt);
            pdcAttributeWriter.applyPdcAttributes(rebuilt, recipe, state);
            applyStrengthenOperations(rebuilt, recipe, state, actionContext);
        }
        return rebuilt;
    }

    private void applyStrengthenOperations(ItemStack itemStack, StrengthenRecipe recipe, StrengthenState state,
            ActionContext actionContext) { new StrengthenAttemptServiceDelegate(this).applyStrengthenOperations(itemStack, recipe, state, actionContext); }

    String buildMaterialsSignature(AttemptPreview preview) { return new StrengthenAttemptServiceDelegate(this).buildMaterialsSignature(preview); }

    static List<Object> materialSignatureRows(List<AttemptMaterial> materials) { return StrengthenAttemptServiceDelegate.materialSignatureRows(materials); }

    Map<String, Object> replacements(AttemptPreview preview, int star) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("star", star);
        replacements.put("temper", preview == null ? 0 : preview.failureTemper());
        if (preview != null && preview.recipe() != null) {
            replacements.put("recipe", preview.recipe().displayName());
        }
        if (preview != null && !preview.costs().isEmpty()) {
            AttemptCost first = preview.costs().get(0);
            replacements.put("cost", first.amount());
            replacements.put("currency", first.displayName());
            replacements.put("costs", renderCosts(preview.costs()));
        } else {
            replacements.put("cost", 0);
            replacements.put("currency", freeCostLabel());
            replacements.put("costs", freeCostLabel());
        }
        return replacements;
    }

    private String renderCosts(List<AttemptCost> costs) { return new StrengthenAttemptServiceDelegate(this).renderCosts(costs); }

    String freeCostLabel() { return new StrengthenAttemptServiceDelegate(this).freeCostLabel(); }

    private Set<Integer> collectFirstReach(Set<Integer> currentFlags, int targetStar) { return new StrengthenAttemptServiceDelegate(this).collectFirstReach(currentFlags, targetStar); }

    void applyPdcAttributes(ItemStack itemStack, StrengthenRecipe recipe, StrengthenState state) { new StrengthenAttemptServiceDelegate(this).applyPdcAttributes(itemStack, recipe, state); }

    void clearPdcAttributes(ItemStack itemStack) { new StrengthenAttemptServiceDelegate(this).clearPdcAttributes(itemStack); }

    void preserveOtherAttributePayloads(ItemStack original, ItemStack rebuilt) { new StrengthenAttemptServiceDelegate(this).preserveOtherAttributePayloads(original, rebuilt); }

    static StarProgress collectStarProgress(Set<Integer> currentFlags, Set<Integer> reachedNow) { return StrengthenAttemptServiceDelegate.collectStarProgress(currentFlags, reachedNow); }

    static ConditionGroup toCoreConditionGroup(StrengthenConditionGroup group) { return StrengthenAttemptServiceDelegate.toCoreConditionGroup(group); }

    static ConditionNode toCoreConditionNode(StrengthenConditionNode node) { return StrengthenAttemptServiceDelegate.toCoreConditionNode(node); }

    record StoredState(boolean hasLayer,
            String recipeId,
            int currentStar,
            int crackLevel,
            Set<Integer> firstReachFlags,
            int successCount,
            int failureCount,
            long lastAttemptAt,
            String materialsSignature,
            String baseSourceSignature,
            String branchPath) {

        static StoredState empty(ItemSourceRef baseSource, String fallbackSignature) {
            String signature = Texts.isBlank(fallbackSignature) ? ItemSourceUtil.toShorthand(baseSource) : fallbackSignature;
            return new StoredState(false, "", 0, 0, Set.of(), 0, 0, 0L, "", signature, "");
        }

        StoredState withBaseSourceSignature(String fallbackSignature) {
            String resolvedSignature = Texts.isBlank(baseSourceSignature) ? fallbackSignature : baseSourceSignature;
            if (Objects.equals(baseSourceSignature, resolvedSignature)) {
                return this;
            }
            return new StoredState(
                    hasLayer,
                    recipeId,
                    currentStar,
                    crackLevel,
                    firstReachFlags,
                    successCount,
                    failureCount,
                    lastAttemptAt,
                    materialsSignature,
                    resolvedSignature,
                    branchPath
            );
        }
    }

    record ResolvedState(StrengthenState state, StoredState stored) {

    }

    public record BranchSelection(Outcome outcome,
            String errorKey,
            ItemStack rebuilt,
            String branchId,
            String displayName,
            String branchPath,
            Map<String, String> options) {

        public enum Outcome {
            APPLIED,
            PENDING_CHOICE,
            INVALID_CHOICE,
            FAILED
        }

        static BranchSelection failure(String errorKey) {
            return new BranchSelection(Outcome.FAILED, errorKey, null, "", "", "", Map.of());
        }

        static BranchSelection pending(Map<String, StrengthenBranchNode> options) {
            return new BranchSelection(Outcome.PENDING_CHOICE, "", null, "", "", "", StrengthenAttemptServiceSupport.describe(options));
        }

        static BranchSelection invalid(Map<String, StrengthenBranchNode> options) {
            return new BranchSelection(Outcome.INVALID_CHOICE, "", null, "", "", "", StrengthenAttemptServiceSupport.describe(options));
        }

        static BranchSelection applied(ItemStack rebuilt,
                String branchId,
                StrengthenBranchNode node,
                String branchPath) {
            String displayName = node == null || Texts.isBlank(node.displayName()) ? branchId : node.displayName();
            return new BranchSelection(Outcome.APPLIED, "", rebuilt, branchId, displayName, branchPath, Map.of());
        }

    }

    record StarProgress(Set<Integer> updatedFlags, Set<Integer> newlyReached) {

    }

    record AttemptStart(boolean started, AttemptResult existingResult, String errorKey) {
    }

    record JournalEntry(int fingerprint, AttemptResult result) {
    }
}
