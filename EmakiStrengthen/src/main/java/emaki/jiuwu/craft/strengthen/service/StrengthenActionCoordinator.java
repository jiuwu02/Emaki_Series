package emaki.jiuwu.craft.strengthen.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.action.CoreActionItemTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;

public final class StrengthenActionCoordinator {

    private static final long ACTION_TIMEOUT_SECONDS = 30L;

    private final EmakiStrengthenPlugin plugin;

    public StrengthenActionCoordinator(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Boolean> triggerSuccessActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            CoreActionItemTarget itemTarget,
            int star,
            int temper) {
        return triggerSuccessActions(player, recipe, resultSlotId, itemTarget, star, temper, "");
    }

    public CompletableFuture<Boolean> triggerSuccessActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            CoreActionItemTarget itemTarget,
            int star,
            int temper,
            String operationId) {
        return triggerActions(player, recipe, recipe == null ? List.of() : recipe.successActionsForTargetStar(star),
                "strengthen_success", resultSlotId, itemTarget, star, temper, false, false, star, operationId);
    }

    public CompletableFuture<Boolean> triggerFailureActions(Player player,
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

    public CompletableFuture<Boolean> triggerFailureActions(Player player,
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

    private CompletableFuture<Boolean> triggerActions(Player player,
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
        if (recipe == null || player == null || actions == null || actions.isEmpty()) {
            return CompletableFuture.completedFuture(true);
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
        var context = plugin.actionLines().context(player, phase, false, placeholders);
        if (resultItem != null) {
            context = context.with(CoreActionKeys.ITEM, resultItem);
        }
        return plugin.actionLines().run(actions, context, true)
                .orTimeout(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((success, throwable) -> logActionResult(recipe, phase, star, operationId, success, throwable));
    }

    /**
     * Reports a failed action phase.
     *
     * <p>The pipeline reports one aggregate boolean instead of v1's per-line failure list, so the reason a
     * line failed is only in the CoreLib pipeline log; this message names the phase and operation for context.</p>
     */
    private void logActionResult(StrengthenRecipe recipe,
            String phase,
            int star,
            String operationId,
            Boolean success,
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
        if (success == null || !success) {
            plugin.messageService().warning("console.recipe_action_failed", Map.of(
                    "operation_id", operationId == null ? "" : operationId,
                    "recipe", recipe == null ? "-" : recipe.id(),
                    "phase", phase,
                    "star", star,
                    "error", "action batch unsuccessful"
            ));
        }
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
