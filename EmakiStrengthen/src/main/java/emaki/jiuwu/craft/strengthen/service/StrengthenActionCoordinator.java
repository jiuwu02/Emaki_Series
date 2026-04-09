package emaki.jiuwu.craft.strengthen.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.ActionBatchResult;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;
import net.kyori.adventure.text.Component;

public final class StrengthenActionCoordinator {

    private final EmakiStrengthenPlugin plugin;

    public StrengthenActionCoordinator(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public void triggerSuccessActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            ItemStack resultItem,
            int star,
            int temper) {
        triggerActions(player, recipe, recipe == null ? List.of() : recipe.successActionsForTargetStar(star),
                "strengthen_success", resultSlotId, resultItem, star, temper, false, false, star);
    }

    public void triggerFailureActions(Player player,
            StrengthenRecipe recipe,
            String resultSlotId,
            ItemStack resultItem,
            int wasStar,
            int resultStar,
            int temper,
            boolean dropped,
            boolean protectionApplied) {
        triggerActions(player, recipe, recipe == null ? List.of() : recipe.failureActionsForResultStar(resultStar),
                "strengthen_failure", resultSlotId, resultItem, resultStar, temper, dropped, protectionApplied, wasStar);
    }

    private void triggerActions(Player player,
            StrengthenRecipe recipe,
            List<String> actions,
            String phase,
            String resultSlotId,
            ItemStack resultItem,
            int star,
            int temper,
            boolean dropped,
            boolean protectionApplied,
            int wasStar) {
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null || coreLib.actionExecutor() == null || recipe == null || player == null || actions == null || actions.isEmpty()) {
            return;
        }
        String showItem = buildShowItem(resultItem);
        Map<String, String> placeholders = new LinkedHashMap<>();
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
        ActionContext context = new ActionContext(plugin, player, phase, false, placeholders, Map.of(
                "recipe_id", recipe.id(),
                "star", star,
                "temper", temper,
                "result_slot", resultSlotId == null ? "" : resultSlotId,
                "dropped", dropped,
                "protected", protectionApplied,
                "was_star", wasStar
        ));
        coreLib.actionExecutor().executeAll(context, actions, true)
                .whenComplete((result, throwable) -> logActionResult(recipe, phase, star, result, throwable));
    }

    private void logActionResult(StrengthenRecipe recipe,
            String phase,
            int star,
            ActionBatchResult result,
            Throwable throwable) {
        if (throwable != null) {
            plugin.messageService().warning("console.recipe_action_failed", Map.of(
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
            return "物品";
        }
        Component display = itemStack.hasItemMeta() && itemStack.getItemMeta().hasCustomName()
                ? itemStack.getItemMeta().customName()
                : itemStack.effectiveName();
        try {
            return MiniMessages.serialize(display.hoverEvent(itemStack.asHoverEvent(showItem -> showItem)));
        } catch (Exception ignored) {
            return MiniMessages.plain(display);
        }
    }
}
