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

public final class GiveItemAction extends BaseAction {

    private final ItemSourceService itemSourceService;

    public GiveItemAction(ItemSourceService itemSourceService) {
        super(
                "giveitem",
                "item",
                "Give an item source to the current player.",
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
        ItemSource source = ActionItemSourceArguments.resolve(arguments);
        if (source == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid item source for giveitem.");
        }
        if (itemSourceService == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Item source service is not available for giveitem.");
        }
        int amount = Math.max(1, ActionParsers.parseInt(arguments.get("amount"), 1));
        ItemStack itemStack = itemSourceService.createItem(source, amount);
        if (itemStack == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unable to create item from source '" + ItemSourceUtil.toShorthand(source) + "'.");
        }
        if (itemStack.getType().isAir()) {
            return ActionResult.skipped("Created item from source '" + ItemSourceUtil.toShorthand(source) + "' was air.");
        }
        Map<Integer, ItemStack> leftover = context.player().getInventory().addItem(itemStack.clone());
        int dropped = 0;
        for (ItemStack left : leftover.values()) {
            if (left == null || left.getType().isAir()) {
                continue;
            }
            dropped += left.getAmount();
            context.player().getWorld().dropItemNaturally(context.player().getLocation(), left);
        }
        return ActionResult.ok(Map.of(
                "source", Texts.toStringSafe(ItemSourceUtil.toShorthand(source)),
                "amount", itemStack.getAmount(),
                "dropped", dropped
        ));
    }
}
