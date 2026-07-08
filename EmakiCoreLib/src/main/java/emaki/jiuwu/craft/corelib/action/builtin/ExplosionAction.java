package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ExplosionAction extends BaseAction {

    public ExplosionAction() {
        super(
                "explosion",
                "world",
                "Create a conservative Bukkit explosion at a resolved location.",
                ActionParameter.optional("power", ActionParameterType.DOUBLE, "0", "Explosion power"),
                ActionParameter.optional("fire", ActionParameterType.BOOLEAN, "false", "Set fire"),
                ActionParameter.optional("break_blocks", ActionParameterType.BOOLEAN, "false", "Break blocks"),
                ActionParameter.optional("world", ActionParameterType.STRING, "", "World"),
                ActionParameter.optional("x", ActionParameterType.STRING, "", "X"),
                ActionParameter.optional("y", ActionParameterType.STRING, "", "Y"),
                ActionParameter.optional("z", ActionParameterType.STRING, "", "Z")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        if ((context == null || context.player() == null) && (Texts.isBlank(arguments.get("x")) || Texts.isBlank(arguments.get("y")) || Texts.isBlank(arguments.get("z")))) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "explosion requires x, y and z when no player context is available.");
        }
        ActionLocationResolver.ResolvedLocation resolved = ActionLocationResolver.resolve(context, arguments, id());
        if (!resolved.success()) {
            return resolved.error();
        }
        Location location = resolved.location();
        World world = location.getWorld();
        if (world == null) {
            return ActionResult.failure(ActionErrorType.WORLD_NOT_FOUND, "Unknown world for explosion action.");
        }
        double power = Math.max(0D, ActionParsers.parseDouble(arguments.get("power"), 0D));
        if (power <= 0D) {
            return ActionResult.skipped("Explosion power must be greater than zero.");
        }
        boolean fire = Boolean.TRUE.equals(ActionParsers.parseBoolean(arguments.get("fire")));
        boolean breakBlocks = Boolean.TRUE.equals(ActionParsers.parseBoolean(arguments.get("break_blocks")));
        boolean created = world.createExplosion(location.getX(), location.getY(), location.getZ(), (float) power, fire, breakBlocks);
        return ActionResult.ok(Map.of(
                "created", created,
                "power", power,
                "fire", fire,
                "break_blocks", breakBlocks,
                "world", world.getName(),
                "x", location.getX(),
                "y", location.getY(),
                "z", location.getZ()
        ));
    }
}
