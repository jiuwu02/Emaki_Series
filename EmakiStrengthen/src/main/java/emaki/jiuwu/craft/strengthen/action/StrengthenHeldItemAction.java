package emaki.jiuwu.craft.strengthen.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;

public final class StrengthenHeldItemAction implements Action {

    enum Operation {
        RERENDER,
        SET_STAR,
        ADD_STAR,
        REMOVE_STAR,
        RESET_STAR,
        CLEAR_LAYER
    }

    private final EmakiStrengthenPlugin plugin;
    private final String id;
    private final Operation operation;

    StrengthenHeldItemAction(EmakiStrengthenPlugin plugin, String id, Operation operation) {
        this.plugin = plugin;
        this.id = id;
        this.operation = operation;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Modify the action player's held strengthen layer through EmakiStrengthen services.";
    }

    @Override
    public String category() {
        return "emakistrengthen";
    }

    @Override
    public List<ActionParameter> parameters() {
        return switch (operation) {
            case RERENDER, RESET_STAR, CLEAR_LAYER -> List.of();
            case SET_STAR -> List.of(ActionParameter.required("star", ActionParameterType.INTEGER, "Target star."));
            case ADD_STAR -> List.of(ActionParameter.required("amount", ActionParameterType.INTEGER, "Star delta."));
            case REMOVE_STAR -> List.of(ActionParameter.required("amount", ActionParameterType.INTEGER, "Star amount to remove."));
        };
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiStrengthen action requires a player context.");
        }
        ItemStack original = player.getInventory().getItemInMainHand();
        if (original == null || original.getType().isAir()) {
            return ActionResult.skipped("Player is not holding an item.");
        }
        StrengthenState before = plugin.attemptService().readState(original);
        ItemStack updated = switch (operation) {
            case RERENDER -> plugin.attemptService().rebuild(original);
            case SET_STAR -> plugin.attemptService().applyAdminState(original, intArgument(arguments, "star", before.currentStar()), null, null);
            case ADD_STAR -> plugin.attemptService().applyAdminState(original, before.currentStar() + intArgument(arguments, "amount", 0), null, null);
            case REMOVE_STAR -> plugin.attemptService().applyAdminState(original, Math.max(0, before.currentStar() - Math.max(0, intArgument(arguments, "amount", 1))), null, null);
            case RESET_STAR -> plugin.attemptService().applyAdminState(original, 0, null, null);
            case CLEAR_LAYER -> plugin.attemptService().clearStrengthenLayer(original);
        };
        if (updated == null) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "EmakiStrengthen action could not rebuild the held item.");
        }
        player.getInventory().setItemInMainHand(updated);
        StrengthenState after = plugin.attemptService().readState(updated);
        return ActionResult.ok(Map.of("old_star", before.currentStar(), "new_star", after.currentStar(), "has_layer", after.hasLayer()));
    }

    private int intArgument(Map<String, String> arguments, String key, int fallback) {
        String value = arguments == null ? null : arguments.get(key);
        return Texts.isBlank(value) ? fallback : Numbers.tryParseInt(value, fallback);
    }
}
