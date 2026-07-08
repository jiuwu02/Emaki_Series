package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class SetItemAction extends BaseAction {

    private final ItemSourceService itemSourceService;

    public SetItemAction(ItemSourceService itemSourceService) {
        super(
                "setitem",
                "item",
                "Set a player inventory slot to an item source.",
                ActionParameter.optional("slot", ActionParameterType.STRING, "mainhand", "Inventory slot"),
                ActionParameter.optional("source", ActionParameterType.STRING, "", "Item source"),
                ActionParameter.optional("amount", ActionParameterType.INTEGER, "1", "Item amount")
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
        ActionInventorySlot slot = ActionInventorySlot.parse(arguments.get("slot"), "mainhand");
        if (slot == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unsupported setitem slot: " + arguments.get("slot"));
        }
        ItemSource source = ActionItemSourceArguments.resolve(arguments);
        if (source == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid item source for setitem.");
        }
        if (itemSourceService == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Item source service is not available for setitem.");
        }
        int amount = Math.max(1, ActionParsers.parseInt(arguments.get("amount"), 1));
        ItemStack itemStack = itemSourceService.createItem(source, amount);
        if (itemStack == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unable to create item from source '" + ItemSourceUtil.toShorthand(source) + "'.");
        }
        if (itemStack.getType().isAir()) {
            return ActionResult.skipped("Created item from source '" + ItemSourceUtil.toShorthand(source) + "' was air.");
        }
        ItemStack replaced = slot.get(context.player().getInventory());
        boolean replacedItem = replaced != null && !replaced.getType().isAir();
        slot.set(context.player().getInventory(), itemStack.clone());
        return ActionResult.ok(Map.of(
                "slot", slot.id(),
                "source", Texts.toStringSafe(ItemSourceUtil.toShorthand(source)),
                "amount", itemStack.getAmount(),
                "replaced", replacedItem
        ));
    }
}
