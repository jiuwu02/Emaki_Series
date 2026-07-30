package emaki.jiuwu.craft.strengthen.service;

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
import emaki.jiuwu.craft.corelib.api.action.CoreActionItemTarget;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;

public final class StrengthenActionCoordinator {

    private static final long ACTION_TIMEOUT_SECONDS = 30L;

    private final EmakiStrengthenPlugin plugin;
    private final Supplier<ActionExecutor> actionExecutorSupplier;

    public StrengthenActionCoordinator(EmakiStrengthenPlugin plugin, Supplier<ActionExecutor> actionExecutorSupplier) {
        this.plugin = plugin;
        this.actionExecutorSupplier = actionExecutorSupplier;
    }

    public CompletableFuture<ActionBatchResult> triggerSuccessActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            CoreActionItemTarget itemTarget,
            int star,
            int temper) {
        return triggerSuccessActions(player, recipe, resultSlotId, itemTarget, star, temper, "");
    }

    public CompletableFuture<ActionBatchResult> triggerSuccessActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            CoreActionItemTarget itemTarget,
            int star,
            int temper,
            String operationId) {
        return triggerActions(player, recipe, recipe == null ? List.of() : recipe.successActionsForTargetStar(star),
                "strengthen_success", resultSlotId, itemTarget, star, temper, false, false, star, operationId);
    }

    public CompletableFuture<ActionBatchResult> triggerFailureActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            CoreActionItemTarget itemTarget,
            int wasStar,
            int resultStar,
            int temper,
            boolean dropped,
            boolean protectionApplied) {
        return triggerFailureActions(player, recipe, resultSlotId, itemTarget, wasStar, resultStar, temper,
                dropped, protectionApplied, "");
    }

    public CompletableFuture<ActionBatchResult> triggerFailureActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            CoreActionItemTarget itemTarget,
            int wasStar,
            int resultStar,
            int temper,
            boolean dropped,
            boolean protectionApplied,
            String operationId) {
        return triggerActions(player, recipe, recipe == null ? List.of() : recipe.failureActionsForResultStar(resultStar),
                "strengthen_failure", resultSlotId, itemTarget, resultStar, temper, dropped, protectionApplied,
                wasStar, operationId);
    }

    private CompletableFuture<ActionBatchResult> triggerActions(Player player,
            StrengthenRecipe recipe,
            List<String> actions,
            String phase,
            String resultSlotId,
            CoreActionItemTarget itemTarget,
            int star,
            int temper,
            boolean dropped,
            boolean protectionApplied,
            int wasStar,
            String operationId) {
        ActionExecutor actionExecutor = actionExecutorSupplier == null ? null : actionExecutorSupplier.get();
        if (actionExecutor == null || recipe == null || player == null || actions == null || actions.isEmpty()) {
            return CompletableFuture.completedFuture(new ActionBatchResult(true, List.of()));
        }
        ItemStack resultItem = itemTarget == null ? null : itemTarget.itemStack();
        String showItem = buildShowItem(resultItem);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("operation_id", operationId == null ? "" : operationId);
        placeholders.put("strengthen_operation_id", operationId == null ? "" : operationId);
        placeholders.put("strengthen_recipe_id", recipe.id());
        placeholders.put("strengthen_recipe", recipe.displayName());
        placeholders.put("strengthen_star", Integer.toString(star));
        placeholders.put("strengthen_temper", Integer.toString(temper));
        placeholders.put("strengthen_show_item", showItem);
        placeholders.put("strengthen_result_slot", resultSlotId == null ? "" : resultSlotId);
        placeholders.put("show_item", showItem);
        placeholders.put("star", Integer.toString(star));
        placeholders.put("temper", Integer.toString(temper));
        placeholders.put("dropped", Boolean.toString(dropped));
        placeholders.put("protected", Boolean.toString(protectionApplied));
        placeholders.put("was_star", Integer.toString(wasStar));
        placeholders.put("strengthen_max_star", Integer.toString(recipe.limits().maxStar()));
        placeholders.put("strengthen_success_rate", resolveSuccessRate(recipe, wasStar, temper));
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("operationId", operationId == null ? "" : operationId);
        attributes.put("recipe", recipe);
        attributes.put("recipe_id", recipe.id());
        if (resultItem != null) {
            attributes.put("resultItem", resultItem);
        }
        if (itemTarget != null) {
            attributes.put(CoreActionItemTarget.ATTRIBUTE_KEY, itemTarget);
        }
        attributes.put("star", star);
        attributes.put("temper", temper);
        attributes.put("result_slot", resultSlotId == null ? "" : resultSlotId);
        attributes.put("dropped", dropped);
        attributes.put("protected", protectionApplied);
        attributes.put("was_star", wasStar);
        ActionContext context = new ActionContext(plugin, player, phase, false, placeholders, attributes);
        return actionExecutor.executeAll(context, actions, true)
                .orTimeout(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((result, throwable) -> logActionResult(recipe, phase, star, operationId, result, throwable));
    }

    private void logActionResult(StrengthenRecipe recipe,
            String phase,
            int star,
            String operationId,
            ActionBatchResult result,
            Throwable throwable) {
        if (throwable != null) {
            plugin.messageService().warning("console.recipe_action_failed", Map.of(
                    "operation_id", operationId == null ? "" : operationId,
                    "recipe", recipe == null ? "-" : recipe.id(),
                    "phase", phase,
                    "star", star,
                    "error", String.valueOf(throwable.getMessage())
            ));
            return;
        }
        if (result == null || result.success()) {
            return;
        }
        var firstFailure = result.firstFailure();
        plugin.messageService().warning("console.recipe_action_failed", Map.of(
                "operation_id", operationId == null ? "" : operationId,
                "recipe", recipe == null ? "-" : recipe.id(),
                "phase", phase,
                "star", star,
                "error", firstFailure == null || firstFailure.result() == null
                        ? "unknown"
                        : String.valueOf(firstFailure.result().errorMessage())
        ));
    }

    public String buildShowItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return defaultShowItemName();
        }
        try {
            return ItemTextBridge.displayWithItemHoverText(itemStack);
        } catch (Exception _) {
            return ItemTextBridge.effectiveNamePlain(itemStack);
        }
    }

    private String defaultShowItemName() {
        var message = plugin.messageService() == null
                ? ""
                : plugin.messageService().message("strengthen.misc.default_item_name");
        return emaki.jiuwu.craft.corelib.text.Texts.isBlank(message) ? "物品" : message;
    }

    private String resolveSuccessRate(StrengthenRecipe recipe, int currentStar, int temper) {
        ChanceCalculator calculator = plugin.chanceCalculator();
        if (calculator == null) {
            return "0";
        }
        double rate = calculator.calculateSuccessRate(plugin.appConfig(), recipe, currentStar, temper, 0);
        return Numbers.formatNumber(rate, "0.##");
    }
}
