package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
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

public final class DropItemAction extends BaseAction {

    private final ItemSourceService itemSourceService;

    public DropItemAction(ItemSourceService itemSourceService) {
        super(
                "dropitem",
                "item",
                "Drop an item source at a world location.",
                ActionParameter.optional("source", ActionParameterType.STRING, "", "Item source"),
                ActionParameter.required("x", ActionParameterType.STRING, "X"),
                ActionParameter.required("y", ActionParameterType.STRING, "Y"),
                ActionParameter.required("z", ActionParameterType.STRING, "Z"),
                ActionParameter.optional("world", ActionParameterType.STRING, "", "World"),
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
        ItemSource source = ActionItemSourceArguments.resolve(arguments);
        if (source == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid item source for dropitem.");
        }
        ActionLocationResolver.ResolvedLocation resolved = ActionLocationResolver.resolve(context, arguments, id());
        if (!resolved.success()) {
            return resolved.error();
        }
        int amount = Math.max(1, ActionParsers.parseInt(arguments.get("amount"), 1));
        ItemStack itemStack = itemSourceService == null ? null : itemSourceService.createItem(source, amount);
        if (itemStack == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unable to create item from source '" + ItemSourceUtil.toShorthand(source) + "'.");
        }
        if (itemStack.getType().isAir()) {
            return ActionResult.skipped("Created item from source '" + ItemSourceUtil.toShorthand(source) + "' was air.");
        }
        Location location = resolved.location();
        World world = location.getWorld();
        if (world == null) {
            return ActionResult.failure(ActionErrorType.WORLD_NOT_FOUND, "Unknown world for dropitem action.");
        }
        world.dropItem(location, itemStack);
        return ActionResult.ok(Map.of(
                "source", Texts.toStringSafe(ItemSourceUtil.toShorthand(source)),
                "amount", itemStack.getAmount(),
                "world", world.getName(),
                "x", location.getX(),
                "y", location.getY(),
                "z", location.getZ()
        ));
    }
}
