package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

final class ActionLocationResolver {

    private ActionLocationResolver() {
    }

    static ResolvedLocation resolve(ActionContext context, Map<String, String> arguments, String actionId) {
        Location base = context == null || context.player() == null ? null : context.player().getLocation();
        World fallbackWorld = base == null ? null : base.getWorld();
        String requestedWorld = Texts.toStringSafe(arguments.get("world"));
        if (Texts.isBlank(requestedWorld) && fallbackWorld == null) {
            return ResolvedLocation.error(ActionResult.failure(
                    ActionErrorType.INVALID_ARGUMENT,
                    "Action '" + actionId + "' requires 'world' when no player context is available."
            ));
        }
        World world = WorldArgumentResolver.resolve(requestedWorld, fallbackWorld);
        if (world == null) {
            return ResolvedLocation.error(ActionResult.failure(
                    ActionErrorType.WORLD_NOT_FOUND,
                    "Unknown world for action '" + actionId + "'."
            ));
        }
        String x = arguments.get("x");
        String y = arguments.get("y");
        String z = arguments.get("z");
        if (base == null && (isRelative(x) || isRelative(y) || isRelative(z))) {
            return ResolvedLocation.error(ActionResult.failure(
                    ActionErrorType.INVALID_ARGUMENT,
                    "Action '" + actionId + "' cannot use relative coordinates without a player context."
            ));
        }
        double baseX = base == null ? 0D : base.getX();
        double baseY = base == null ? 0D : base.getY();
        double baseZ = base == null ? 0D : base.getZ();
        Location location = new Location(
                world,
                ActionParsers.parseCoordinate(x, baseX),
                ActionParsers.parseCoordinate(y, baseY),
                ActionParsers.parseCoordinate(z, baseZ)
        );
        return ResolvedLocation.ok(location);
    }

    private static boolean isRelative(String raw) {
        return Texts.toStringSafe(raw).trim().startsWith("~");
    }

    record ResolvedLocation(Location location, ActionResult error) {

        static ResolvedLocation ok(Location location) {
            return new ResolvedLocation(location, null);
        }

        static ResolvedLocation error(ActionResult error) {
            return new ResolvedLocation(null, error);
        }

        boolean success() {
            return error == null;
        }
    }
}
