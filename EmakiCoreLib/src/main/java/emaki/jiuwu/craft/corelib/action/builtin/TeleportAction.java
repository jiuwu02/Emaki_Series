package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.Location;
import org.bukkit.World;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;

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
        Location target = resolveTarget(context, arguments);
        if (target == null) {
            return context == null || context.player() == null
                    ? requirePlayerResult(context)
                    : ActionResult.failure(ActionErrorType.WORLD_NOT_FOUND, "Unknown world for teleport action.");
        }
        return context.player().teleport(target)
                ? ActionResult.ok()
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Teleport was rejected.");
    }

    @Override
    public CompletionStage<ActionResult> executeAsync(ActionContext context, Map<String, String> arguments) {
        Location target = resolveTarget(context, arguments);
        if (target == null) {
            return CompletableFuture.completedFuture(
                    context == null || context.player() == null
                            ? requirePlayerResult(context)
                            : ActionResult.failure(ActionErrorType.WORLD_NOT_FOUND,
                                    "Unknown world for teleport action."));
        }
        return context.player().teleportAsync(target).thenApply(success -> Boolean.TRUE.equals(success)
                ? ActionResult.ok()
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Teleport was rejected."));
    }

    private Location resolveTarget(ActionContext context, Map<String, String> arguments) {
        if (context == null || context.player() == null) {
            return null;
        }
        Location base = context.player().getLocation();
        World world = WorldArgumentResolver.resolve(arguments.get("world"), base.getWorld());
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                ActionParsers.parseCoordinate(arguments.get("x"), base.getX()),
                ActionParsers.parseCoordinate(arguments.get("y"), base.getY()),
                ActionParsers.parseCoordinate(arguments.get("z"), base.getZ()),
                (float) ActionParsers.parseDouble(arguments.get("yaw"), base.getYaw()),
                (float) ActionParsers.parseDouble(arguments.get("pitch"), base.getPitch())
        );
    }
}
