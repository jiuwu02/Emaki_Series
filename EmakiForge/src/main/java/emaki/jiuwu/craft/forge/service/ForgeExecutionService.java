package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;

import emaki.jiuwu.craft.corelib.action.ActionBatchResult;
import emaki.jiuwu.craft.corelib.api.action.CoreActionItemTarget;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore.GuaranteeCounterUpdate;
import emaki.jiuwu.craft.forge.model.ForgeResult;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.ValidationResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.script.js.JavaScriptForgeResultHookRegistry;
import emaki.jiuwu.craft.forge.script.js.JavaScriptForgeRuleRegistry;

final class ForgeExecutionService {

    private static final String DEFAULT_ACTION_FAILURE_KEY = "forge.error.action_failed";

    private final EmakiForgePlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;
    private final SessionValidator sessionValidator;
    private final ForgeActionCoordinator actionCoordinator;
    private final QualityCalculationService qualityCalculationService;
    private final ForgePlanResolver forgePlanResolver;
    private final ResultItemGiver resultItemGiver;
    private final CraftRecorder craftRecorder;
    private final ResultItemPostProcessor resultItemPostProcessor;
    private final ForgeFailureResolver forgeFailureResolver;
    private final JavaScriptForgeRuleRegistry javaScriptForgeRuleRegistry;
    private final JavaScriptForgeResultHookRegistry javaScriptResultHookRegistry;

    ForgeExecutionService(EmakiForgePlugin plugin,
                          ExecutionDispatcher executionDispatcher,
                          ThreadOwnership threadOwnership,
                          SessionValidator sessionValidator,
                          ForgeActionCoordinator actionCoordinator,
                          QualityCalculationService qualityCalculationService,
                          ForgePlanResolver forgePlanResolver,
                          ResultItemGiver resultItemGiver,
                          CraftRecorder craftRecorder,
                          ResultItemPostProcessor resultItemPostProcessor,
                          JavaScriptForgeRuleRegistry javaScriptForgeRuleRegistry,
                          JavaScriptForgeResultHookRegistry javaScriptResultHookRegistry) {
        this(plugin, executionDispatcher, threadOwnership, sessionValidator, actionCoordinator,
                qualityCalculationService, forgePlanResolver, resultItemGiver,
                craftRecorder, resultItemPostProcessor, new ForgeFailureResolver(),
                javaScriptForgeRuleRegistry, javaScriptResultHookRegistry);
    }

    ForgeExecutionService(EmakiForgePlugin plugin,
                          ExecutionDispatcher executionDispatcher,
                          ThreadOwnership threadOwnership,
                          SessionValidator sessionValidator,
                          ForgeActionCoordinator actionCoordinator,
                          QualityCalculationService qualityCalculationService,
                          ForgePlanResolver forgePlanResolver,
                          ResultItemGiver resultItemGiver,
                          CraftRecorder craftRecorder,
                          ResultItemPostProcessor resultItemPostProcessor,
                          ForgeFailureResolver forgeFailureResolver,
                          JavaScriptForgeRuleRegistry javaScriptForgeRuleRegistry,
                          JavaScriptForgeResultHookRegistry javaScriptResultHookRegistry) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
        this.sessionValidator = sessionValidator;
        this.actionCoordinator = actionCoordinator;
        this.qualityCalculationService = qualityCalculationService;
        this.forgePlanResolver = forgePlanResolver;
        this.resultItemGiver = resultItemGiver;
        this.craftRecorder = craftRecorder;
        this.resultItemPostProcessor = resultItemPostProcessor;
        this.forgeFailureResolver = forgeFailureResolver;
        this.javaScriptForgeRuleRegistry = javaScriptForgeRuleRegistry;
        this.javaScriptResultHookRegistry = javaScriptResultHookRegistry;
    }

    CompletableFuture<ForgeResult> execute(Player player,
                                           Recipe recipe,
                                           GuiItems guiItems,
                                           ForgeService.PreparedForge preparedForge,
                                           ValidationResult validation,
                                           long sessionGeneration,
                                           long runtimeGeneration,
                                           BooleanSupplier deliveryClaim,
                                           Runnable deliveryRollback,
                                           Runnable deliveryCommit) {
        ForgeResult result = new ForgeResult();
        if (!isRuntimeCurrent(runtimeGeneration)) {
            return CompletableFuture.completedFuture(staleRuntimeResult());
        }
        if (validation != null && !validation.success()) {
            result.setErrorKey(validation.errorKey());
            result.setReplacements(validation.replacements());
            return finishAsync(player, recipe, result, runtimeGeneration).toCompletableFuture();
        }
        return actionCoordinator.executePhase(player, recipe, guiItems, "pre", null, null, 1D, null, null)
                .thenCompose(preBatch -> callPlayerOwnerAsync(player, sessionGeneration, () -> prepareResultActions(
                        player, recipe, guiItems, preparedForge, sessionGeneration, runtimeGeneration, result, preBatch,
                        deliveryClaim, deliveryRollback, deliveryCommit)));
    }

    private CompletionStage<ForgeResult> prepareResultActions(Player player,
                                                              Recipe recipe,
                                                              GuiItems guiItems,
                                                              ForgeService.PreparedForge preparedForge,
                                                              long sessionGeneration,
                                                              long runtimeGeneration,
                                                              ForgeResult result,
                                                              ActionBatchResult preBatch,
                                                              BooleanSupplier deliveryClaim,
                                                              Runnable deliveryRollback,
                                                              Runnable deliveryCommit) {
        if (!isRuntimeCurrent(runtimeGeneration)) {
            return CompletableFuture.completedFuture(staleRuntimeResult());
        }
        if (!preBatch.success()) {
            buildActionFailure(result, preBatch);
            return finishFailureAsync(player, recipe, guiItems, result, runtimeGeneration,
                    result.actionFailureReason());
        }
        if (recipe.hasFailureMechanism()) {
            JavaScriptForgeRuleRegistry.Decision decision = applyJavaScriptForgeRules(
                    player, recipe, guiItems, recipe.successRate());
            if (!isRuntimeCurrent(runtimeGeneration)) {
                return CompletableFuture.completedFuture(staleRuntimeResult());
            }
            if (decision.cancelled()) {
                result.setErrorKey("forge.craft.failed");
                result.setReplacements(Map.of(
                        "outcome_type", Texts.isBlank(decision.message()) ? "cancelled" : decision.message()));
                return finishFailureAsync(player, recipe, guiItems, result, runtimeGeneration, "cancelled");
            }
            double roll = ThreadLocalRandom.current().nextDouble(100D);
            if (roll >= decision.successRate()) {
                ForgeFailureResolver.ForgeFailureResult failureResult = forgeFailureResolver.resolve(recipe, guiItems, player);
                result.setErrorKey("forge.craft.failed");
                result.setReplacements(Map.of("outcome_type", failureResult.outcomeType()));
                return finishFailureAsync(player, recipe, guiItems, result, runtimeGeneration,
                        failureResult.outcomeType());
            }
        }

        ForgeService.PreparedForge forgePlan = forgePlanResolver.resolve(player, recipe, guiItems, preparedForge);
        if (forgePlan == null || forgePlan.request() == null) {
            result.setErrorKey("forge.error.item_create");
            result.setReplacements(Map.of());
            return finishFailureAsync(player, recipe, guiItems, result, runtimeGeneration,
                    "Unable to prepare forge assembly request.");
        }
        result.setQuality(forgePlan.quality());
        result.setMultiplier(forgePlan.multiplier());
        ItemStack resultItem = resultItemGiver.preview(forgePlan.request());
        if (resultItem == null) {
            result.setErrorKey("forge.error.item_create");
            result.setReplacements(Map.of());
            return finishFailureAsync(player, recipe, guiItems, result, runtimeGeneration,
                    "Unable to create forge result item.");
        }
        if (resultItemPostProcessor != null) {
            resultItemPostProcessor.process(player, recipe, guiItems, forgePlan, resultItem);
        }

        CoreActionItemTarget itemTarget = new CoreActionItemTarget(resultItem);
        return awaitPhaseIfCurrent(player, recipe, guiItems, "result", resultItem, itemTarget,
                        result.quality(), result.multiplier(), null, null, runtimeGeneration)
                .thenCompose(_ -> awaitPhaseIfCurrent(player, recipe, guiItems, "success", resultItem,
                        itemTarget, result.quality(), result.multiplier(), null, null, runtimeGeneration))
                .thenCompose(_ -> awaitQualityActionsIfCurrent(player, recipe, guiItems, resultItem,
                        itemTarget, forgePlan, result, runtimeGeneration))
                .thenCompose(_ -> callPlayerOwnerAsync(player, sessionGeneration,
                        () -> deliverResult(player, recipe, guiItems, forgePlan, sessionGeneration,
                                runtimeGeneration, result, itemTarget,
                                deliveryClaim, deliveryRollback, deliveryCommit)));
    }

    private CompletionStage<ForgeResult> deliverResult(Player player,
                                      Recipe recipe,
                                      GuiItems guiItems,
                                      ForgeService.PreparedForge forgePlan,
                                      long sessionGeneration,
                                      long runtimeGeneration,
                                      ForgeResult result,
                                      CoreActionItemTarget itemTarget,
                                      BooleanSupplier deliveryClaim,
                                      Runnable deliveryRollback,
                                      Runnable deliveryCommit) {
        if (!isRuntimeCurrent(runtimeGeneration)) {
            return CompletableFuture.completedFuture(staleRuntimeResult());
        }
        boolean deliveryReserved = false;
        if (deliveryClaim != null) {
            try {
                if (!deliveryClaim.getAsBoolean()) {
                    return CompletableFuture.completedFuture(staleRuntimeResult());
                }
                deliveryReserved = true;
                if (!isRuntimeCurrent(runtimeGeneration)) {
                    releaseDeliveryReservation(true, deliveryRollback);
                    return CompletableFuture.completedFuture(staleRuntimeResult());
                }
            } catch (Throwable throwable) {
                result.setErrorKey("forge.error.action_failed");
                result.setReplacements(Map.of("reason", Texts.toStringSafe(throwable.getMessage())));
                return finishFailureAsync(player, recipe, guiItems, result, runtimeGeneration,
                        "Unable to reserve forge result delivery.");
            }
        }
        ItemStack resultItem = itemTarget.itemStack();
        boolean delivered;
        try {
            delivered = resultItemGiver.deliver(player, resultItem);
        } catch (Throwable throwable) {
            releaseDeliveryReservation(deliveryReserved, deliveryRollback);
            result.setErrorKey("forge.error.item_create");
            result.setReplacements(Map.of("reason", Texts.toStringSafe(throwable.getMessage())));
            return finishFailureAsync(player, recipe, guiItems, result, runtimeGeneration,
                    "Forge result delivery threw an exception.");
        }
        if (!delivered) {
            releaseDeliveryReservation(deliveryReserved, deliveryRollback);
            result.setErrorKey("forge.error.item_create");
            result.setReplacements(Map.of());
            return finishFailureAsync(player, recipe, guiItems, result, runtimeGeneration,
                    "Unable to deliver forge result item.");
        }
        result.setSuccess(true);
        result.setResultItem(resultItem);
        if (deliveryCommit != null) {
            try {
                deliveryCommit.run();
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Forge result delivery commit callback failed: "
                        + Texts.toStringSafe(throwable.getMessage()));
            }
        }
        // Delivery is the commit boundary: stale completions must not return consumed inputs again.
        if (!isRuntimeCurrent(runtimeGeneration)) {
            return CompletableFuture.completedFuture(result);
        }
        if (player != null) {
            GuaranteeCounterUpdate guaranteeUpdate = qualityCalculationService.resolveGuaranteeUpdate(
                    player.getUniqueId(),
                    sessionGeneration,
                    recipe,
                    forgePlan.rolledQualityTier(),
                    forgePlan.forceQualityApplied()
            );
            craftRecorder.record(player.getUniqueId(), sessionGeneration, recipe.id(), guaranteeUpdate);
        }
        return finishAsync(player, recipe, result, runtimeGeneration);
    }

    private CompletableFuture<ForgeResult> callPlayerOwnerAsync(Player player,
                                                                long generation,
                                                                Supplier<? extends CompletionStage<ForgeResult>> operation) {
        if (player == null || plugin == null || operation == null || executionDispatcher == null) {
            return CompletableFuture.completedFuture(staleSessionResult());
        }
        CompletableFuture<ForgeResult> future = new CompletableFuture<>();
        Runnable task = () -> {
            if (sessionValidator == null || !sessionValidator.isCurrent(player.getUniqueId(), generation)) {
                future.complete(staleSessionResult());
                return;
            }
            try {
                CompletionStage<ForgeResult> stage = operation.get();
                if (stage == null) {
                    future.completeExceptionally(new IllegalStateException(
                            "Forge player-owner operation returned no completion stage."));
                    return;
                }
                stage.whenComplete((value, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                    } else {
                        future.complete(value);
                    }
                });
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };
        if (threadOwnership != null && threadOwnership.isEntityOwned(player)) {
            task.run();
            return future;
        }
        try {
            var scheduled = executionDispatcher.runEntity(plugin, player, task,
                    () -> future.completeExceptionally(new RejectedExecutionException(
                            "Forge player-owner operation retired before execution.")));
            if (scheduled == null) {
                future.completeExceptionally(new RejectedExecutionException("Forge player-owner scheduling was rejected."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private CompletableFuture<ForgeResult> callPlayerOwner(Player player,
                                                           long generation,
                                                           Supplier<ForgeResult> operation) {
        if (player == null || plugin == null || operation == null || executionDispatcher == null) {
            return CompletableFuture.completedFuture(staleSessionResult());
        }
        CompletableFuture<ForgeResult> future = new CompletableFuture<>();
        Runnable task = () -> {
            if (sessionValidator == null || !sessionValidator.isCurrent(player.getUniqueId(), generation)) {
                future.complete(staleSessionResult());
                return;
            }
            try {
                future.complete(operation.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };
        if (threadOwnership != null && threadOwnership.isEntityOwned(player)) {
            task.run();
            return future;
        }
        try {
            var scheduled = executionDispatcher.runEntity(plugin, player, task,
                    () -> future.completeExceptionally(new RejectedExecutionException(
                            "Forge player-owner operation retired before execution.")));
            if (scheduled == null) {
                future.completeExceptionally(new RejectedExecutionException("Forge player-owner scheduling was rejected."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private void releaseDeliveryReservation(boolean reserved, Runnable deliveryRollback) {
        if (!reserved || deliveryRollback == null) {
            return;
        }
        try {
            deliveryRollback.run();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Forge result delivery rollback callback failed: "
                    + Texts.toStringSafe(throwable.getMessage()));
        }
    }

    private ForgeResult staleSessionResult() {
        ForgeResult result = new ForgeResult();
        result.setErrorKey(DEFAULT_ACTION_FAILURE_KEY);
        result.setReplacements(Map.of("reason", "player session is no longer current"));
        return result;
    }

    private boolean isRuntimeCurrent(long generation) {
        boolean current = plugin != null && plugin.isGenerationActive(generation);
        if (!current && plugin != null) {
            plugin.runtimeMetrics().recordExecutionStale();
        }
        return current;
    }

    private ForgeResult staleRuntimeResult() {
        ForgeResult result = new ForgeResult();
        result.setErrorKey("forge.error.runtime_unavailable");
        result.setReplacements(Map.of("reason", "runtime generation changed"));
        return result;
    }

    private CompletionStage<Void> awaitPhaseIfCurrent(Player player,
            Recipe recipe,
            GuiItems guiItems,
            String phase,
            ItemStack resultItem,
            CoreActionItemTarget itemTarget,
            String quality,
            double multiplier,
            String errorKey,
            String failureReason,
            long runtimeGeneration) {
        if (!isRuntimeCurrent(runtimeGeneration)) {
            return CompletableFuture.completedFuture(null);
        }
        return actionCoordinator.awaitPhase(player, recipe, guiItems, phase, resultItem, itemTarget,
                quality, multiplier, errorKey, failureReason);
    }

    private CompletionStage<Void> awaitQualityActionsIfCurrent(Player player,
            Recipe recipe,
            GuiItems guiItems,
            ItemStack resultItem,
            CoreActionItemTarget itemTarget,
            ForgeService.PreparedForge forgePlan,
            ForgeResult result,
            long runtimeGeneration) {
        if (!isRuntimeCurrent(runtimeGeneration)) {
            return CompletableFuture.completedFuture(null);
        }
        return actionCoordinator.awaitQualityActions(player, recipe, guiItems, resultItem, itemTarget,
                forgePlan.qualityTier(), result.quality(), result.multiplier());
    }

    private CompletionStage<ForgeResult> finishFailureAsync(Player player,
            Recipe recipe,
            GuiItems guiItems,
            ForgeResult result,
            long runtimeGeneration,
            String failureReason) {
        if (!isRuntimeCurrent(runtimeGeneration)) {
            return CompletableFuture.completedFuture(staleRuntimeResult());
        }
        return actionCoordinator.awaitPhase(player, recipe, guiItems, "failure", null, null,
                        result.quality(), result.multiplier(), result.errorKey(), failureReason)
                .thenCompose(_ -> finishAsync(player, recipe, result, runtimeGeneration));
    }

    private CompletionStage<ForgeResult> finishAsync(Player player,
            Recipe recipe,
            ForgeResult result,
            long runtimeGeneration) {
        if (!isRuntimeCurrent(runtimeGeneration)) {
            return CompletableFuture.completedFuture(result != null && result.success()
                    ? result
                    : staleRuntimeResult());
        }
        if (javaScriptResultHookRegistry == null) {
            return CompletableFuture.completedFuture(result);
        }
        CompletionStage<Void> hookCompletion = javaScriptResultHookRegistry.fire(
                player, recipe, result, runtimeGeneration);
        if (hookCompletion == null) {
            return CompletableFuture.completedFuture(result);
        }
        return hookCompletion.handle((ignored, throwable) -> {
            if (throwable != null && plugin != null) {
                plugin.getLogger().warning("[JavaScript] Forge result hook completion failed: "
                        + Texts.toStringSafe(throwable.getMessage()));
            }
            if (isRuntimeCurrent(runtimeGeneration) || result.success()) {
                return result;
            }
            return staleRuntimeResult();
        });
    }

    private JavaScriptForgeRuleRegistry.Decision applyJavaScriptForgeRules(Player player, Recipe recipe, GuiItems guiItems, double successRate) {
        if (javaScriptForgeRuleRegistry == null) {
            return new JavaScriptForgeRuleRegistry.Decision(
                    recipe == null ? "" : recipe.id(),
                    recipe == null ? "" : recipe.displayName(),
                    player == null ? "" : player.getUniqueId().toString(),
                    player == null ? "" : player.getName(),
                    successRate,
                    successRate,
                    false,
                    "",
                    java.util.Map.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
        return javaScriptForgeRuleRegistry.apply(player, recipe, guiItems, successRate);
    }

    private void buildActionFailure(ForgeResult result, ActionBatchResult batch) {
        var failure = batch.firstFailure();
        String reason = actionCoordinator.resolveFailureReason(failure);
        result.setErrorKey(DEFAULT_ACTION_FAILURE_KEY);
        result.setActionFailureReason(reason);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("reason", reason);
        if (failure != null) {
            replacements.put("action", failure.actionId());
            replacements.put("line", failure.lineNumber());
        }
        result.setReplacements(Map.copyOf(replacements));
    }

    @FunctionalInterface
    interface SessionValidator {

        boolean isCurrent(UUID playerId, long generation);
    }

    @FunctionalInterface
    interface ForgePlanResolver {

        ForgeService.PreparedForge resolve(Player player, Recipe recipe, GuiItems guiItems, ForgeService.PreparedForge preparedForge);
    }

    @FunctionalInterface
    interface ResultItemGiver {

        ItemStack give(Player player, EmakiItemAssemblyRequest request);

        default ItemStack preview(EmakiItemAssemblyRequest request) {
            return give(null, request);
        }

        default boolean deliver(Player player, ItemStack itemStack) {
            if (player == null || itemStack == null || itemStack.getType().isAir()) {
                return false;
            }
            java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack.clone());
            leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            return true;
        }
    }

    @FunctionalInterface
    interface CraftRecorder {

        void record(UUID playerId,
                    long generation,
                    String recipeId,
                    GuaranteeCounterUpdate guaranteeUpdate);
    }

    @FunctionalInterface
    interface ResultItemPostProcessor {

        void process(Player player, Recipe recipe, GuiItems guiItems, ForgeService.PreparedForge preparedForge, ItemStack resultItem);
    }
}
