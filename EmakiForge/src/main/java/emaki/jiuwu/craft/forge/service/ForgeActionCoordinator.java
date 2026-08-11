package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.pipeline.ActionLineRunner;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.api.action.CoreActionItemTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeActionCoordinator {

    private static final long ACTION_TIMEOUT_SECONDS = 30L;

    /**
     * Returned to callers that need a human-readable reason when the Boolean result provides no step detail.
     *
     * <p>The pipeline runner answers with a pass/fail verdict; per-step failure information such as action
     * ids and line numbers is not surfaced at this layer. Callers log or display this constant instead.</p>
     */
    static final String UNKNOWN_FAILURE_REASON = "Unknown forge action failure.";

    private final EmakiForgePlugin plugin;
    private final ForgeResultItemFactory resultItemFactory;
    private final ActionLineRunner actionLines;

    /**
     * Creates a coordinator.
     *
     * @param plugin the owning plugin
     * @param resultItemFactory resolves display names and show-item placeholders
     * @param actionLines the pipeline runner; safe to hold because it reads the live engine per call
     */
    ForgeActionCoordinator(EmakiForgePlugin plugin,
            ForgeResultItemFactory resultItemFactory,
            ActionLineRunner actionLines) {
        this.plugin = plugin;
        this.resultItemFactory = resultItemFactory;
        this.actionLines = actionLines;
    }

    /**
     * Runs the phase for a given item target and waits for the result.
     *
     * @param player the acting player
     * @param recipe the recipe being forged
     * @param guiItems the GUI item slots
     * @param phase phase name
     * @param resultItem the resolved result item (may be {@code null} for pre-phase)
     * @param quality the resolved quality label
     * @param multiplier the stat multiplier
     * @param errorKey the error key to surface on failure
     * @param failureReason a human-readable failure reason
     * @return {@code true} when all lines succeeded
     */
    CompletableFuture<Boolean> executePhase(Player player,
            Recipe recipe,
            GuiItems guiItems,
            String phase,
            ItemStack resultItem,
            String quality,
            double multiplier,
            String errorKey,
            String failureReason) {
        return executePhase(player, recipe, guiItems, phase, resultItem, null, quality, multiplier, errorKey, failureReason);
    }

    /**
     * Runs the phase for an explicit item target and waits for the result.
     *
     * @param player the acting player
     * @param recipe the recipe being forged
     * @param guiItems the GUI item slots
     * @param phase phase name
     * @param resultItem the resolved result item
     * @param itemTarget mutable holder that stages write the item back through; may be {@code null}
     * @param quality the resolved quality label
     * @param multiplier the stat multiplier
     * @param errorKey the error key to surface on failure
     * @param failureReason a human-readable failure reason
     * @return {@code true} when all lines succeeded
     */
    CompletableFuture<Boolean> executePhase(Player player,
            Recipe recipe,
            GuiItems guiItems,
            String phase,
            ItemStack resultItem,
            CoreActionItemTarget itemTarget,
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
                itemTarget,
                quality,
                multiplier,
                errorKey,
                failureReason
        );
    }

    /**
     * Fires a phase without waiting for it.
     *
     * @param player the acting player
     * @param recipe the recipe being forged
     * @param guiItems the GUI item slots
     * @param phase phase name
     * @param resultItem the resolved result item
     * @param quality the resolved quality label
     * @param multiplier the stat multiplier
     * @param errorKey the error key to surface on failure
     * @param failureMessage a human-readable failure message
     */
    void triggerPhase(Player player,
            Recipe recipe,
            GuiItems guiItems,
            String phase,
            ItemStack resultItem,
            String quality,
            double multiplier,
            String errorKey,
            String failureMessage) {
        awaitPhase(player, recipe, guiItems, phase, resultItem, null, quality, multiplier, errorKey, failureMessage);
    }

    /**
     * Runs the phase and logs any failure, discarding the Boolean result.
     *
     * @param player the acting player
     * @param recipe the recipe being forged
     * @param guiItems the GUI item slots
     * @param phase phase name
     * @param resultItem the resolved result item
     * @param itemTarget mutable holder for item write-back; may be {@code null}
     * @param quality the resolved quality label
     * @param multiplier the stat multiplier
     * @param errorKey the error key to surface on failure
     * @param failureMessage a human-readable failure message
     * @return a future that completes when the phase finishes
     */
    CompletableFuture<Void> awaitPhase(Player player,
            Recipe recipe,
            GuiItems guiItems,
            String phase,
            ItemStack resultItem,
            CoreActionItemTarget itemTarget,
            String quality,
            double multiplier,
            String errorKey,
            String failureMessage) {
        return executePhase(player, recipe, guiItems, phase, resultItem, itemTarget, quality, multiplier, errorKey, failureMessage)
                .handle((success, throwable) -> {
                    if (throwable != null) {
                        logWarning("console.forge_phase_execution_failed", Map.of(
                                "phase", phase,
                                "recipe", recipe == null ? "" : recipe.id(),
                                "error", String.valueOf(throwable.getMessage())
                        ));
                    } else if (!Boolean.TRUE.equals(success)) {
                        logWarning("console.forge_phase_failed", Map.of(
                                "phase", phase,
                                "recipe", recipe == null ? "" : recipe.id(),
                                "reason", UNKNOWN_FAILURE_REASON
                        ));
                    }
                    return null;
                });
    }

    /**
     * Runs quality-tier item-meta actions and logs any failure.
     *
     * @param player the acting player
     * @param recipe the recipe being forged
     * @param guiItems the GUI item slots
     * @param resultItem the resolved result item
     * @param itemTarget mutable holder for item write-back; may be {@code null}
     * @param qualityTier the resolved quality tier
     * @param quality the quality label
     * @param multiplier the stat multiplier
     * @return a future that completes when the quality actions finish
     */
    CompletableFuture<Void> awaitQualityActions(Player player,
            Recipe recipe,
            GuiItems guiItems,
            ItemStack resultItem,
            CoreActionItemTarget itemTarget,
            QualitySettings.QualityTier qualityTier,
            String quality,
            double multiplier) {
        if (qualityTier == null) {
            return CompletableFuture.completedFuture(null);
        }
        List<String> lines = plugin.appConfig().qualitySettings().itemMetaActions(qualityTier.name());
        if (lines.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return executeActionLines(player, recipe, guiItems, "quality", lines, resultItem, itemTarget,
                quality, multiplier, null, null)
                .handle((success, throwable) -> {
                    if (throwable != null) {
                        logWarning("console.forge_quality_execution_failed", Map.of(
                                "tier", qualityTier.name(),
                                "error", String.valueOf(throwable.getMessage())
                        ));
                    } else if (!Boolean.TRUE.equals(success)) {
                        logWarning("console.forge_quality_failed", Map.of(
                                "tier", qualityTier.name(),
                                "reason", UNKNOWN_FAILURE_REASON
                        ));
                    }
                    return null;
                });
    }

    private CompletableFuture<Boolean> executeActionLines(Player player,
            Recipe recipe,
            GuiItems guiItems,
            String phase,
            List<String> lines,
            ItemStack resultItem,
            CoreActionItemTarget itemTarget,
            String quality,
            double multiplier,
            String errorKey,
            String failureReason) {
        if (lines.isEmpty() || actionLines == null || !actionLines.available()) {
            return CompletableFuture.completedFuture(true);
        }
        Map<String, String> placeholders = buildPlaceholders(player, recipe, guiItems, phase,
                resultItem, itemTarget, quality, multiplier, errorKey, failureReason);

        /*
         * Pass the action item as CoreActionKeys.ITEM so stages that mutate items (e.g.
         * item_component_add) can read and write it in place. After the batch completes the updated
         * item is stored back into itemTarget so the caller gets the post-action item at delivery.
         */
        ItemStack actionItem = itemTarget == null ? resultItem : itemTarget.itemStack();
        PipelineContext context = actionLines.context(player, phase, false, placeholders);
        if (actionItem != null) {
            context = context.with(CoreActionKeys.ITEM, actionItem);
        }

        final PipelineContext finalContext = context;
        return actionLines.run(lines, finalContext, true)
                .thenApply(success -> {
                    // Write the (possibly mutated) item back into the caller's holder.
                    if (itemTarget != null && actionItem != null) {
                        ItemStack updated = finalContext.get(CoreActionKeys.ITEM).orElse(null);
                        if (updated != null && updated != actionItem) {
                            itemTarget.setItemStack(updated);
                        }
                    }
                    return success;
                })
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

    private Map<String, String> buildPlaceholders(Player player,
            Recipe recipe,
            GuiItems guiItems,
            String phase,
            ItemStack resultItem,
            CoreActionItemTarget itemTarget,
            String quality,
            double multiplier,
            String errorKey,
            String failureReason) {
        ItemStack actionItem = itemTarget == null ? resultItem : itemTarget.itemStack();
        Map<String, String> placeholders = new LinkedHashMap<>();
        String sourceItemName = resultItemFactory.resolveSourceItemName(guiItems, actionItem, recipe);
        String showItem = resultItemFactory.buildShowItemPlaceholder(guiItems, recipe, actionItem);
        placeholders.put("forge_recipe_id", recipe == null ? "" : recipe.id());
        placeholders.put("forge_recipe_name", recipe == null ? "" : recipe.displayName());
        placeholders.put("forge_source_item_name", sourceItemName);
        placeholders.put("forge_result_item_name", resultItemFactory.resolveResultItemName(recipe, actionItem));
        placeholders.put("forge_quality", Texts.toStringSafe(quality));
        placeholders.put("forge_multiplier", Numbers.formatNumber(multiplier, plugin.appConfig().defaultNumberFormat()));
        placeholders.put("forge_multiplier_raw", Double.toString(multiplier));
        placeholders.put("forge_error_key", Texts.toStringSafe(errorKey));
        placeholders.put("forge_failure_reason", Texts.toStringSafe(failureReason));
        placeholders.put("forge_show_item", showItem);
        return placeholders;
    }

    private void logWarning(String key, Map<String, ?> replacements) {
        try {
            plugin.messageService().warning(key, replacements);
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Forge result action warning could not be emitted | key=" + key
                    + " | error=" + exception.getMessage());
        }
    }

}
