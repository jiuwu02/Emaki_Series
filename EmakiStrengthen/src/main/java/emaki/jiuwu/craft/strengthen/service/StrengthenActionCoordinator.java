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
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementAttemptResult;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipe;

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

    public CompletableFuture<Boolean> triggerEnhancementActions(Player player,
            EnhancementRecipe recipe,
            ItemStack resultItem,
            EnhancementAttemptResult result,
            String operationId) {
        if (recipe == null || player == null || result == null) {
            return CompletableFuture.completedFuture(true);
        }
        List<String> actions = result.actionPhaseKeys().stream()
                .flatMap(key -> recipe.actions().getOrDefault(key, List.of()).stream())
                .toList();
        if (actions.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        String phase = result.success() ? "enhancement_success" : "enhancement_failure";
        Map<String, String> placeholders = new LinkedHashMap<>(result.toPlaceholders());
        placeholders.put("operation_id", operationId == null ? "" : operationId);
        placeholders.put("strengthen_operation_id", operationId == null ? "" : operationId);
        placeholders.put("strengthen_recipe_id", recipe.id());
        placeholders.put("enhancement_recipe_id", recipe.id());
        placeholders.put("enhancement_mode", recipe.mode());
        placeholders.put("enhancement_provider", recipe.target().provider());
        var context = plugin.actionLines().context(player, phase, false, placeholders);
        if (resultItem != null) {
            context = context.with(CoreActionKeys.ITEM, resultItem);
        }
        return plugin.actionLines().run(actions, context, true)
                .orTimeout(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((success, throwable) -> logEnhancementActionResult(
                        recipe, phase, operationId, success, throwable));
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

    private void logEnhancementActionResult(EnhancementRecipe recipe,
            String phase,
            String operationId,
            Boolean success,
            Throwable throwable) {
        if (throwable == null && Boolean.TRUE.equals(success)) {
            return;
        }
        plugin.messageService().warning("console.recipe_action_failed", Map.of(
                "operation_id", operationId == null ? "" : operationId,
                "recipe", recipe == null ? "-" : recipe.id(),
                "phase", phase,
                "star", "-",
                "error", throwable == null ? "action batch unsuccessful" : String.valueOf(throwable.getMessage())
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
        return Texts.isBlank(message) ? "物品" : message;
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
