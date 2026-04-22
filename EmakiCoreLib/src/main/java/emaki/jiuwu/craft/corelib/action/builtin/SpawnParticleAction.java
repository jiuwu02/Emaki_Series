package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class SpawnParticleAction extends BaseAction {

    public SpawnParticleAction() {
        super(
                "spawnparticle",
                "feedback",
                "Spawn particles.",
                ActionParameter.required("particle", ActionParameterType.STRING, "Particle key"),
                ActionParameter.optional("count", ActionParameterType.INTEGER, "1", "Particle count"),
                ActionParameter.optional("target", ActionParameterType.STRING, "player", "Target"),
                ActionParameter.optional("world", ActionParameterType.STRING, "", "World"),
                ActionParameter.optional("x", ActionParameterType.STRING, "", "X"),
                ActionParameter.optional("y", ActionParameterType.STRING, "", "Y"),
                ActionParameter.optional("z", ActionParameterType.STRING, "", "Z"),
                ActionParameter.optional("offset_x", ActionParameterType.DOUBLE, "0", "Offset x"),
                ActionParameter.optional("offset_y", ActionParameterType.DOUBLE, "0", "Offset y"),
                ActionParameter.optional("offset_z", ActionParameterType.DOUBLE, "0", "Offset z"),
                ActionParameter.optional("extra", ActionParameterType.DOUBLE, "0", "Extra")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Particle particle = ActionParsers.parseParticle(stringArg(arguments, "particle"));
        if (particle == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown particle: " + stringArg(arguments, "particle"));
        }
        Location location;
        String target = Texts.lower(arguments.get("target"));
        if (!"location".equals(target)) {
            ActionResult playerCheck = requirePlayerResult(context);
            if (!playerCheck.success()) {
                return playerCheck;
            }
            location = context.player().getLocation();
        } else {
            World world = WorldArgumentResolver.resolve(
                    arguments.get("world"),
                    context.player() == null ? null : context.player().getWorld()
            );
            if (world == null) {
                return ActionResult.failure(ActionErrorType.WORLD_NOT_FOUND, "Unknown world for particle action.");
            }
            double baseX = context.player() == null ? 0D : context.player().getLocation().getX();
            double baseY = context.player() == null ? 0D : context.player().getLocation().getY();
            double baseZ = context.player() == null ? 0D : context.player().getLocation().getZ();
            location = new Location(
                    world,
                    ActionParsers.parseCoordinate(arguments.get("x"), baseX),
                    ActionParsers.parseCoordinate(arguments.get("y"), baseY),
                    ActionParsers.parseCoordinate(arguments.get("z"), baseZ)
            );
        }
        location.getWorld().spawnParticle(
                particle,
                location,
                ActionParsers.parseInt(arguments.get("count"), 1),
                ActionParsers.parseDouble(arguments.get("offset_x"), 0D),
                ActionParsers.parseDouble(arguments.get("offset_y"), 0D),
                ActionParsers.parseDouble(arguments.get("offset_z"), 0D),
                ActionParsers.parseDouble(arguments.get("extra"), 0D)
        );
        return ActionResult.ok();
    }
}
