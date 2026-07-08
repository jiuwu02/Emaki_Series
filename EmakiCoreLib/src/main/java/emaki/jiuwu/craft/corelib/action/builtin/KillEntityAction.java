package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class KillEntityAction extends BaseAction {

    public KillEntityAction() {
        super(
                "killentity",
                "entity",
                "Remove nearby entities matching conservative filters.",
                ActionParameter.optional("type", ActionParameterType.STRING, "", "Entity type filter"),
                ActionParameter.optional("radius", ActionParameterType.DOUBLE, "1", "Search radius"),
                ActionParameter.optional("limit", ActionParameterType.INTEGER, "1", "Maximum entities to remove"),
                ActionParameter.optional("include_players", ActionParameterType.BOOLEAN, "false", "Allow killing players"),
                ActionParameter.optional("world", ActionParameterType.STRING, "", "World"),
                ActionParameter.optional("x", ActionParameterType.STRING, "", "X"),
                ActionParameter.optional("y", ActionParameterType.STRING, "", "Y"),
                ActionParameter.optional("z", ActionParameterType.STRING, "", "Z")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        EntityType type = parseEntityType(arguments.get("type"));
        if (Texts.isNotBlank(arguments.get("type")) && type == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown entity type: " + arguments.get("type"));
        }
        if ((context == null || context.player() == null) && (Texts.isBlank(arguments.get("x")) || Texts.isBlank(arguments.get("y")) || Texts.isBlank(arguments.get("z")))) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "killentity requires x, y and z when no player context is available.");
        }
        ActionLocationResolver.ResolvedLocation resolved = ActionLocationResolver.resolve(context, arguments, id());
        if (!resolved.success()) {
            return resolved.error();
        }
        Location location = resolved.location();
        World world = location.getWorld();
        if (world == null) {
            return ActionResult.failure(ActionErrorType.WORLD_NOT_FOUND, "Unknown world for killentity action.");
        }
        double radius = Math.max(0D, ActionParsers.parseDouble(arguments.get("radius"), 1D));
        int limit = Math.max(1, ActionParsers.parseInt(arguments.get("limit"), 1));
        boolean includePlayers = Boolean.TRUE.equals(ActionParsers.parseBoolean(arguments.get("include_players")));
        List<Entity> candidates = world.getNearbyEntities(location, radius, radius, radius).stream()
                .filter(entity -> entity != null && !entity.isDead())
                .filter(entity -> includePlayers || !(entity instanceof Player))
                .filter(entity -> type == null || entity.getType() == type)
                .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(location)))
                .limit(limit)
                .toList();
        if (candidates.isEmpty()) {
            return ActionResult.skipped("No matching entities found for killentity.");
        }
        for (Entity entity : candidates) {
            entity.remove();
        }
        return ActionResult.ok(Map.of(
                "removed", candidates.size(),
                "include_players", includePlayers,
                "type", type == null ? "" : type.name().toLowerCase(Locale.ROOT),
                "radius", radius
        ));
    }

    private EntityType parseEntityType(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        String normalized = Texts.trim(raw).replace("minecraft:", "").replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return EntityType.valueOf(normalized);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}
