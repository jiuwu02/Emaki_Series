package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class BreakBlockAction extends BaseAction {

    public BreakBlockAction() {
        super(
                "breakblock",
                "world",
                "Break or clear a block at a resolved location.",
                ActionParameter.optional("drop_items", ActionParameterType.BOOLEAN, "false", "Drop block items"),
                ActionParameter.optional("apply_physics", ActionParameterType.BOOLEAN, "true", "Apply physics when clearing"),
                ActionParameter.optional("world", ActionParameterType.STRING, "", "World"),
                ActionParameter.optional("x", ActionParameterType.STRING, "", "X"),
                ActionParameter.optional("y", ActionParameterType.STRING, "", "Y"),
                ActionParameter.optional("z", ActionParameterType.STRING, "", "Z")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null && (Texts.isBlank(arguments.get("x")) || Texts.isBlank(arguments.get("y")) || Texts.isBlank(arguments.get("z")))) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "breakblock requires x, y and z when no player context is available.");
        }
        ActionLocationResolver.ResolvedLocation resolved = ActionLocationResolver.resolve(context, arguments, id());
        if (!resolved.success()) {
            return resolved.error();
        }
        Location location = resolved.location();
        World world = location.getWorld();
        if (world == null) {
            return ActionResult.failure(ActionErrorType.WORLD_NOT_FOUND, "Unknown world for breakblock action.");
        }
        Block block = location.getBlock();
        Material before = block.getType();
        if (before.isAir()) {
            return ActionResult.skipped("Target block is already air.");
        }
        ActionResult eventCheck = callBreakEvent(player, block);
        if (!eventCheck.success() || eventCheck.skipped()) {
            return eventCheck;
        }
        boolean dropItems = Boolean.TRUE.equals(ActionParsers.parseBoolean(arguments.get("drop_items")));
        boolean broken;
        if (dropItems) {
            broken = player == null
                    ? block.breakNaturally()
                    : block.breakNaturally(player.getInventory().getItemInMainHand());
        } else {
            boolean applyPhysics = !Boolean.FALSE.equals(ActionParsers.parseBoolean(arguments.get("apply_physics")));
            block.setType(Material.AIR, applyPhysics);
            broken = true;
        }
        if (!broken) {
            return ActionResult.skipped("Block was not broken.");
        }
        return ActionResult.ok(Map.of(
                "world", world.getName(),
                "x", block.getX(),
                "y", block.getY(),
                "z", block.getZ(),
                "before", before.name().toLowerCase(java.util.Locale.ROOT),
                "drop_items", dropItems
        ));
    }

    private ActionResult callBreakEvent(Player player, Block block) {
        if (player == null) {
            return ActionResult.ok();
        }
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled()
                ? ActionResult.skipped("Block break was cancelled.")
                : ActionResult.ok();
    }
}
