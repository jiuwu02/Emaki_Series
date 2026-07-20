package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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
import emaki.jiuwu.craft.forge.script.js.JavaScriptForgeResultHookRegistry;
import emaki.jiuwu.craft.forge.script.js.JavaScriptForgeRuleRegistry;

final class ForgeExecutionService {

    private static final String DEFAULT_ACTION_FAILURE_KEY = "forge.error.action_failed";

    private final Plugin plugin;
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

    ForgeExecutionService(Plugin plugin,
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

    ForgeExecutionService(Plugin plugin,
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
            long sessionGeneration) {
        ForgeResult result = new ForgeResult();
        if (validation != null && !validation.success()) {
            result.setErrorKey(validation.errorKey());
            result.setReplacements(validation.replacements());
            return CompletableFuture.completedFuture(finish(player, recipe, result));
        }
        return actionCoordinator.executePhase(player, recipe, guiItems, "pre", null, null, 1D, null, null)
                .thenCompose(preBatch -> callPlayerOwnerAsync(player, sessionGeneration, () -> prepareResultActions(
                        player, recipe, guiItems, preparedForge, sessionGeneration, result, preBatch)));
    }

    private CompletionStage<ForgeResult> prepareResultActions(Player player,
            Recipe recipe,
            GuiItems guiItems,
            ForgeService.PreparedForge preparedForge,
            long sessionGeneration,
            ForgeResult result,
            ActionBatchResult preBatch) {
        if (!preBatch.success()) {
            return CompletableFuture.completedFuture(buildActionFailure(player, recipe, guiItems, result, preBatch));
        }
        if (recipe.hasFailureMechanism()) {
            JavaScriptForgeRuleRegistry.Decision decision = applyJavaScriptForgeRules(
                    player, recipe, guiItems, recipe.successRate());
            if (decision.cancelled()) {
                result.setErrorKey("forge.craft.failed");
                result.setReplacements(Map.of(
                        "outcome_type", Texts.isBlank(decision.message()) ? "cancelled" : decision.message()));
                actionCoordinator.triggerPhase(player, recipe, guiItems, "failure", null, null, 1D,
                        result.errorKey(), "cancelled");
                return CompletableFuture.completedFuture(finish(player, recipe, result));
            }
            double roll = ThreadLocalRandom.current().nextDouble(100D);
            if (roll >= decision.successRate()) {
                ForgeFailureResolver.ForgeFailureResult failureResult = forgeFailureResolver.resolve(recipe, guiItems, player);
                result.setErrorKey("forge.craft.failed");
                result.setReplacements(Map.of("outcome_type", failureResult.outcomeType()));
                actionCoordinator.triggerPhase(player, recipe, guiItems, "failure", null, null, 1D,
                        result.errorKey(), failureResult.outcomeType());
                return CompletableFuture.completedFuture(finish(player, recipe, result));
            }
        }

        ForgeService.PreparedForge forgePlan = forgePlanResolver.resolve(player, recipe, guiItems, preparedForge);
        if (forgePlan == null || forgePlan.request() == null) {
            result.setErrorKey("forge.error.item_create");
            result.setReplacements(Map.of());
            actionCoordinator.triggerPhase(player, recipe, guiItems, "failure", null, null, 1D,
                    result.errorKey(), "Unable to prepare forge assembly request.");
            return CompletableFuture.completedFuture(finish(player, recipe, result));
        }
        result.setQuality(forgePlan.quality());
        result.setMultiplier(forgePlan.multiplier());
        ItemStack resultItem = resultItemGiver.preview(forgePlan.request());
        if (resultItem == null) {
            result.setErrorKey("forge.error.item_create");
            result.setReplacements(Map.of());
            actionCoordinator.triggerPhase(player, recipe, guiItems, "failure", null, result.quality(),
                    result.multiplier(), result.errorKey(), "Unable to create forge result item.");
            return CompletableFuture.completedFuture(finish(player, recipe, result));
        }
        if (resultItemPostProcessor != null) {
            resultItemPostProcessor.process(player, recipe, guiItems, forgePlan, resultItem);
        }

        CoreActionItemTarget itemTarget = new CoreActionItemTarget(resultItem);
        return actionCoordinator.awaitPhase(player, recipe, guiItems, "result", resultItem, itemTarget,
                result.quality(), result.multiplier(), null, null)
                .thenCompose(_ -> actionCoordinator.awaitPhase(player, recipe, guiItems, "success", resultItem,
                        itemTarget, result.quality(), result.multiplier(), null, null))
                .thenCompose(_ -> actionCoordinator.awaitQualityActions(player, recipe, guiItems, resultItem,
                        itemTarget, forgePlan.qualityTier(), result.quality(), result.multiplier()))
                .thenCompose(_ -> callPlayerOwner(player, sessionGeneration,
                        () -> deliverResult(player, recipe, guiItems, forgePlan, sessionGeneration, result, itemTarget)));
    }

    private ForgeResult deliverResult(Player player,
            Recipe recipe,
            GuiItems guiItems,
            ForgeService.PreparedForge forgePlan,
            long sessionGeneration,
            ForgeResult result,
            CoreActionItemTarget itemTarget) {
        ItemStack resultItem = itemTarget.itemStack();
        if (!resultItemGiver.deliver(player, resultItem)) {
            result.setErrorKey("forge.error.item_create");
            result.setReplacements(Map.of());
            actionCoordinator.triggerPhase(player, recipe, guiItems, "failure", null, result.quality(),
                    result.multiplier(), result.errorKey(), "Unable to deliver forge result item.");
            return finish(player, recipe, result);
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
        result.setSuccess(true);
        result.setResultItem(resultItem);
        return finish(player, recipe, result);
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

    private ForgeResult staleSessionResult() {
        ForgeResult result = new ForgeResult();
        result.setErrorKey(DEFAULT_ACTION_FAILURE_KEY);
        result.setReplacements(Map.of("reason", "player session is no longer current"));
        return result;
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

    private ForgeResult finish(Player player, Recipe recipe, ForgeResult result) {
        if (javaScriptResultHookRegistry != null) {
            javaScriptResultHookRegistry.fire(player, recipe, result);
        }
        return result;
    }

    private ForgeResult buildActionFailure(Player player, Recipe recipe, GuiItems guiItems, ForgeResult result, ActionBatchResult batch) {
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
        actionCoordinator.triggerPhase(
                player,
                recipe,
                guiItems,
                "failure",
                result.resultItem(),
                result.quality(),
                result.multiplier(),
                result.errorKey(),
                reason
        );
        return finish(player, recipe, result);
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
