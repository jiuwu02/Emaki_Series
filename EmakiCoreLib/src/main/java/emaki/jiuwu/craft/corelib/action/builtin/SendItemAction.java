package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class SendItemAction extends BaseAction {

    public SendItemAction() {
        super(
                "senditem",
                "item",
                "Send a temporary item to the current player.",
                ActionParameter.required("id", ActionParameterType.STRING, "Temporary item id"),
                ActionParameter.optional("keep", ActionParameterType.BOOLEAN, "false", "Keep item in temporary store after sending")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        String itemId = stringArg(arguments, "id");
        if (Texts.isBlank(itemId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "senditem requires a non-empty 'id'.");
        }
        TemporaryItemStore store = TemporaryItemStore.from(context);
        boolean keep = Boolean.TRUE.equals(ActionParsers.parseBoolean(arguments.get("keep")));
        ItemStack itemStack = keep ? store.get(itemId) : store.remove(itemId);
        if (itemStack == null || itemStack.getType().isAir()) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Temporary item not found: " + itemId);
        }
        Map<Integer, ItemStack> leftover = context.player().getInventory().addItem(itemStack.clone());
        leftover.values().forEach(left -> context.player().getWorld().dropItemNaturally(context.player().getLocation(), left));
        return ActionResult.ok(Map.of(
                "id", Texts.lower(itemId),
                "amount", itemStack.getAmount()
        ));
    }
}
