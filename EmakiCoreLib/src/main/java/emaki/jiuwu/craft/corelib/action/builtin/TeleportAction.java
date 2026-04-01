package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class TeleportAction extends BaseAction {

    public TeleportAction() {
        super(
                "teleport",
                "player",
                "Teleport the player.",
                ActionParameter.required("x", ActionParameterType.STRING, "X"),
                ActionParameter.required("y", ActionParameterType.STRING, "Y"),
                ActionParameter.required("z", ActionParameterType.STRING, "Z"),
                ActionParameter.optional("world", ActionParameterType.STRING, "", "World"),
                ActionParameter.optional("yaw", ActionParameterType.DOUBLE, "0", "Yaw"),
                ActionParameter.optional("pitch", ActionParameterType.DOUBLE, "0", "Pitch")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        Location base = context.player().getLocation();
        World world = Texts.isBlank(arguments.get("world")) ? base.getWorld() : Bukkit.getWorld(arguments.get("world"));
        if (world == null) {
            return ActionResult.failure(ActionErrorType.WORLD_NOT_FOUND, "Unknown world for teleport action.");
        }
        Location target = new Location(
                world,
                ActionParsers.parseCoordinate(arguments.get("x"), base.getX()),
                ActionParsers.parseCoordinate(arguments.get("y"), base.getY()),
                ActionParsers.parseCoordinate(arguments.get("z"), base.getZ()),
                (float) ActionParsers.parseDouble(arguments.get("yaw"), base.getYaw()),
                (float) ActionParsers.parseDouble(arguments.get("pitch"), base.getPitch())
        );
        context.player().teleport(target);
        return ActionResult.ok();
    }
}
