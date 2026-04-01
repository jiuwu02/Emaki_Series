package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionBatchResult;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.forge.model.ForgeResult;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.ValidationResult;

final class ForgeExecutionService {

    private static final String DEFAULT_ACTION_FAILURE_KEY = "forge.error.action_failed";

    private final ForgeActionCoordinator actionCoordinator;
    private final QualityCalculationService qualityCalculationService;
    private final ForgePlanResolver forgePlanResolver;
    private final ResultItemGiver resultItemGiver;
    private final CraftRecorder craftRecorder;

    ForgeExecutionService(ForgeActionCoordinator actionCoordinator,
            QualityCalculationService qualityCalculationService,
            ForgePlanResolver forgePlanResolver,
            ResultItemGiver resultItemGiver,
            CraftRecorder craftRecorder) {
        this.actionCoordinator = actionCoordinator;
        this.qualityCalculationService = qualityCalculationService;
        this.forgePlanResolver = forgePlanResolver;
        this.resultItemGiver = resultItemGiver;
        this.craftRecorder = craftRecorder;
    }

    CompletableFuture<ForgeResult> execute(Player player,
            Recipe recipe,
            GuiItems guiItems,
            ForgeService.PreparedForge preparedForge,
            ValidationResult validation) {
        ForgeResult result = new ForgeResult();
        if (validation != null && !validation.success()) {
            result.setErrorKey(validation.errorKey());
            result.setReplacements(validation.replacements());
            return CompletableFuture.completedFuture(result);
        }
        return actionCoordinator.executePhase(player, recipe, guiItems, "pre", null, null, 1D, null, null)
                .thenApply(preBatch -> {
                    if (!preBatch.success()) {
                        return buildActionFailure(player, recipe, guiItems, result, preBatch);
                    }
                    ForgeService.PreparedForge forgePlan = forgePlanResolver.resolve(player, recipe, guiItems, preparedForge);
                    if (forgePlan == null || forgePlan.request() == null) {
                        result.setErrorKey("forge.error.item_create");
                        result.setReplacements(Map.of());
                        actionCoordinator.triggerPhase(player, recipe, guiItems, "failure", null, null, 1D, result.errorKey(), "Unable to prepare forge assembly request.");
                        return result;
                    }
                    result.setQuality(forgePlan.quality());
                    result.setMultiplier(forgePlan.multiplier());
                    ItemStack resultItem = resultItemGiver.give(player, forgePlan.request());
                    if (resultItem == null) {
                        result.setErrorKey("forge.error.item_create");
                        result.setReplacements(Map.of());
                        actionCoordinator.triggerPhase(player, recipe, guiItems, "failure", null, result.quality(), result.multiplier(), result.errorKey(), "Unable to create forge result item.");
                        return result;
                    }
                    if (player != null) {
                        qualityCalculationService.applyGuaranteeOutcome(
                                player.getUniqueId(),
                                recipe,
                                forgePlan.rolledQualityTier(),
                                forgePlan.forceQualityApplied()
                        );
                        craftRecorder.record(player.getUniqueId(), recipe.id());
                    }
                    result.setSuccess(true);
                    result.setResultItem(resultItem);
                    actionCoordinator.triggerPhase(player, recipe, guiItems, "result", resultItem, result.quality(), result.multiplier(), null, null);
                    actionCoordinator.triggerPhase(player, recipe, guiItems, "success", resultItem, result.quality(), result.multiplier(), null, null);
                    actionCoordinator.triggerQualityActions(player, recipe, guiItems, resultItem, forgePlan.qualityTier(), result.quality(), result.multiplier());
                    return result;
                });
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
        return result;
    }

    @FunctionalInterface
    interface ForgePlanResolver {

        ForgeService.PreparedForge resolve(Player player, Recipe recipe, GuiItems guiItems, ForgeService.PreparedForge preparedForge);
    }

    @FunctionalInterface
    interface ResultItemGiver {

        ItemStack give(Player player, EmakiItemAssemblyRequest request);
    }

    @FunctionalInterface
    interface CraftRecorder {

        void record(UUID playerId, String recipeId);
    }
}
