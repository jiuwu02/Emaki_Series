package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class TakeItemAction extends BaseAction {

    private final ItemSourceService itemSourceService;

    public TakeItemAction(ItemSourceService itemSourceService) {
        super(
                "takeitem",
                "item",
                "Take matching items from the player inventory.",
                ActionParameter.optional("source", ActionParameterType.STRING, "", "Expected item source"),
                ActionParameter.optional("amount", ActionParameterType.INTEGER, "1", "Amount to take")
        );
        this.itemSourceService = itemSourceService;
    }

    @Override
    public boolean acceptsDynamicParameter(String name) {
        return ActionItemSourceArguments.isAlias(name);
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        ItemSource source = ActionItemSourceArguments.resolve(arguments);
        if (source == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid item source for takeitem.");
        }
        if (itemSourceService == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Item source service is not available for takeitem.");
        }
        long amount = Math.max(1L, ActionParsers.parseInt(arguments.get("amount"), 1));
        long available = InventoryItemUtil.countItems(context.player(), itemSourceService, source);
        if (available < amount) {
            return ActionResult.skipped("Not enough matching items for takeitem: requested " + amount + ", available " + available + ".");
        }
        boolean removed = InventoryItemUtil.removeItems(context.player().getInventory(), itemSourceService, source, amount);
        if (!removed) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Failed to remove matching items for takeitem.");
        }
        return ActionResult.ok(Map.of(
                "source", Texts.toStringSafe(ItemSourceUtil.toShorthand(source)),
                "amount", amount,
                "available_before", available
        ));
    }
}
