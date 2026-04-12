package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionBatchResult;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.action.ActionStepResult;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeActionCoordinator {

    private static final long ACTION_TIMEOUT_SECONDS = 30L;

    private final EmakiForgePlugin plugin;
    private final ForgeResultItemFactory resultItemFactory;
    private final Supplier<ActionExecutor> actionExecutorSupplier;

    ForgeActionCoordinator(EmakiForgePlugin plugin,
            ForgeResultItemFactory resultItemFactory,
            Supplier<ActionExecutor> actionExecutorSupplier) {
        this.plugin = plugin;
        this.resultItemFactory = resultItemFactory;
        this.actionExecutorSupplier = actionExecutorSupplier;
    }

    CompletableFuture<ActionBatchResult> executePhase(Player player,
            Recipe recipe,
            GuiItems guiItems,
            String phase,
            ItemStack resultItem,
            String quality,
            double multiplier,
            String errorKey,
            String failureReason) {
        return executeActionLines(
                player,
                recipe,
                guiItems,
                phase,
                phaseLines(recipe, phase),
                resultItem,
                quality,
                multiplier,
                errorKey,
                failureReason
        );
    }

    void triggerPhase(Player player,
            Recipe recipe,
            GuiItems guiItems,
            String phase,
            ItemStack resultItem,
            String quality,
            double multiplier,
            String errorKey,
            String failureMessage) {
        executePhase(player, recipe, guiItems, phase, resultItem, quality, multiplier, errorKey, failureMessage)
                .whenComplete((batch, throwable) -> {
                    if (throwable != null) {
                        plugin.messageService().warning("console.forge_phase_execution_failed", Map.of(
                                "phase", phase,
                                "recipe", recipe.id(),
                                "error", String.valueOf(throwable.getMessage())
                        ));
                        return;
                    }
                    if (batch != null && !batch.success()) {
                        plugin.messageService().warning("console.forge_phase_failed", Map.of(
                                "phase", phase,
                                "recipe", recipe.id(),
                                "reason", resolveFailureReason(batch.firstFailure())
                        ));
                    }
                });
    }

    void triggerQualityActions(Player player,
            Recipe recipe,
            GuiItems guiItems,
            ItemStack resultItem,
            QualitySettings.QualityTier qualityTier,
            String quality,
            double multiplier) {
        if (qualityTier == null) {
            return;
        }
        List<String> lines = plugin.appConfig().qualitySettings().itemMetaActions(qualityTier.name());
        if (lines.isEmpty()) {
            return;
        }
        executeActionLines(player, recipe, guiItems, "quality", lines, resultItem, quality, multiplier, null, null)
                .whenComplete((batch, throwable) -> {
                    if (throwable != null) {
                        plugin.messageService().warning("console.forge_quality_execution_failed", Map.of(
                                "tier", qualityTier.name(),
                                "error", String.valueOf(throwable.getMessage())
                        ));
                        return;
                    }
                    if (batch != null && !batch.success()) {
                        plugin.messageService().warning("console.forge_quality_failed", Map.of(
                                "tier", qualityTier.name(),
                                "reason", resolveFailureReason(batch.firstFailure())
                        ));
                    }
                });
    }

    String resolveFailureReason(ActionStepResult failure) {
        if (failure == null || failure.result() == null) {
            return "Unknown forge action failure.";
        }
        if (Texts.isNotBlank(failure.result().errorMessage())) {
            return failure.result().errorMessage();
        }
        return "Action '" + failure.actionId() + "' failed.";
    }

    private CompletableFuture<ActionBatchResult> executeActionLines(Player player,
            Recipe recipe,
            GuiItems guiItems,
            String phase,
            List<String> lines,
            ItemStack resultItem,
            String quality,
            double multiplier,
            String errorKey,
            String failureReason) {
        var actionExecutor = actionExecutorSupplier == null ? null : actionExecutorSupplier.get();
        if (lines.isEmpty() || actionExecutor == null) {
            return CompletableFuture.completedFuture(new ActionBatchResult(true, List.of()));
        }
        ActionContext context = buildActionContext(player, recipe, guiItems, phase, resultItem, quality, multiplier, errorKey, failureReason);
        return actionExecutor
                .executeAll(context, lines, true)
                .orTimeout(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private List<String> phaseLines(Recipe recipe, String phase) {
        if (recipe == null) {
            return List.of();
        }
        return switch (Texts.lower(phase)) {
            case "pre" ->
                recipe.action() == null ? List.of() : recipe.action().pre();
            case "result" ->
                recipe.result() == null ? List.of() : recipe.result().action();
            case "success" ->
                recipe.action() == null ? List.of() : recipe.action().success();
            case "failure" ->
                recipe.action() == null ? List.of() : recipe.action().failure();
            default ->
                List.of();
        };
    }

    private ActionContext buildActionContext(Player player,
            Recipe recipe,
            GuiItems guiItems,
            String phase,
            ItemStack resultItem,
            String quality,
            double multiplier,
            String errorKey,
            String failureReason) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        String sourceItemName = resultItemFactory.resolveSourceItemName(guiItems, resultItem, recipe);
        String showItem = resultItemFactory.buildShowItemPlaceholder(guiItems, recipe, resultItem);
        placeholders.put("forge_recipe_id", recipe == null ? "" : recipe.id());
        placeholders.put("forge_recipe_name", recipe == null ? "" : recipe.displayName());
        placeholders.put("forge_source_item_name", sourceItemName);
        placeholders.put("forge_result_item_name", resultItemFactory.resolveResultItemName(recipe, resultItem));
        placeholders.put("forge_quality", Texts.toStringSafe(quality));
        placeholders.put("forge_multiplier", Numbers.formatNumber(multiplier, plugin.appConfig().defaultNumberFormat()));
        placeholders.put("forge_multiplier_raw", Double.toString(multiplier));
        placeholders.put("forge_error_key", Texts.toStringSafe(errorKey));
        placeholders.put("forge_failure_reason", Texts.toStringSafe(failureReason));
        placeholders.put("forge_show_item", showItem);
        placeholders.put("show_item", showItem);

        Map<String, Object> attributes = new LinkedHashMap<>();
        putIfNotNull(attributes, "recipe", recipe);
        putIfNotNull(attributes, "guiItems", guiItems);
        putIfNotNull(attributes, "targetItem", guiItems == null ? null : guiItems.targetItem());
        putIfNotNull(attributes, "resultItem", resultItem);
        putIfNotNull(attributes, "quality", quality);
        attributes.put("multiplier", multiplier);
        putIfNotNull(attributes, "errorKey", errorKey);
        putIfNotNull(attributes, "failureReason", failureReason);

        return new ActionContext(plugin, player, phase, false, placeholders, attributes);
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (target != null && value != null) {
            target.put(key, value);
        }
    }
}
