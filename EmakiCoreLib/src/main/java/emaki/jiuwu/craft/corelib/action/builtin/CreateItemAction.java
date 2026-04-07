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

public final class CreateItemAction extends BaseAction {

    private final ItemSourceService itemSourceService;

    public CreateItemAction(ItemSourceService itemSourceService) {
        super(
                "createitem",
                "item",
                "Create a temporary item from an item source.",
                ActionParameter.required("id", ActionParameterType.STRING, "Temporary item id"),
                ActionParameter.optional("source", ActionParameterType.STRING, "", "Item source"),
                ActionParameter.optional("amount", ActionParameterType.INTEGER, "1", "Item amount")
        );
        this.itemSourceService = itemSourceService;
    }

    @Override
    public boolean acceptsDynamicParameter(String name) {
        String normalized = Texts.lower(name);
        return "item".equals(normalized) || "item_source".equals(normalized);
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        String itemId = stringArg(arguments, "id");
        if (Texts.isBlank(itemId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "createitem requires a non-empty 'id'.");
        }
        ItemSource source = resolveSource(arguments);
        if (source == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid item source for createitem.");
        }
        int amount = Math.max(1, ActionParsers.parseInt(arguments.get("amount"), 1));
        ItemStack itemStack = itemSourceService == null ? null : itemSourceService.createItem(source, amount);
        if (itemStack == null || itemStack.getType().isAir()) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unable to create item from source '" + ItemSourceUtil.toShorthand(source) + "'.");
        }
        TemporaryItemStore.from(context).put(itemId, itemStack);
        return ActionResult.ok(Map.of(
                "id", Texts.lower(itemId),
                "source", Texts.toStringSafe(ItemSourceUtil.toShorthand(source)),
                "amount", itemStack.getAmount()
        ));
    }

    private ItemSource resolveSource(Map<String, String> arguments) {
        String raw = stringArg(arguments, "source");
        if (Texts.isBlank(raw)) {
            raw = stringArg(arguments, "item");
        }
        if (Texts.isBlank(raw)) {
            raw = stringArg(arguments, "item_source");
        }
        return ItemSourceUtil.parse(raw);
    }
}
