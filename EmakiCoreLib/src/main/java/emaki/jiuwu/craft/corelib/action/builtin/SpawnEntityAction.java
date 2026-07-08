package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class SpawnEntityAction extends BaseAction {

    public SpawnEntityAction() {
        super(
                "spawnentity",
                "entity",
                "Spawn a Bukkit entity at a resolved location.",
                ActionParameter.required("type", ActionParameterType.STRING, "Entity type"),
                ActionParameter.optional("world", ActionParameterType.STRING, "", "World"),
                ActionParameter.optional("x", ActionParameterType.STRING, "", "X"),
                ActionParameter.optional("y", ActionParameterType.STRING, "", "Y"),
                ActionParameter.optional("z", ActionParameterType.STRING, "", "Z"),
                ActionParameter.optional("count", ActionParameterType.INTEGER, "1", "Entity count")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        EntityType type = parseEntityType(arguments.get("type"));
        if (type == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown entity type: " + arguments.get("type"));
        }
        if (!type.isSpawnable()) {
            return ActionResult.skipped("Entity type '" + type.name().toLowerCase(Locale.ROOT) + "' is not spawnable.");
        }
        if ((context == null || context.player() == null) && (Texts.isBlank(arguments.get("x")) || Texts.isBlank(arguments.get("y")) || Texts.isBlank(arguments.get("z")))) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "spawnentity requires x, y and z when no player context is available.");
        }
        ActionLocationResolver.ResolvedLocation resolved = ActionLocationResolver.resolve(context, arguments, id());
        if (!resolved.success()) {
            return resolved.error();
        }
        Location location = resolved.location();
        World world = location.getWorld();
        if (world == null) {
            return ActionResult.failure(ActionErrorType.WORLD_NOT_FOUND, "Unknown world for spawnentity action.");
        }
        int count = Math.max(1, ActionParsers.parseInt(arguments.get("count"), 1));
        Entity last = null;
        for (int index = 0; index < count; index++) {
            last = world.spawnEntity(location, type);
        }
        return ActionResult.ok(Map.of(
                "type", type.name().toLowerCase(Locale.ROOT),
                "count", count,
                "world", world.getName(),
                "x", location.getX(),
                "y", location.getY(),
                "z", location.getZ(),
                "last_uuid", last == null ? "" : last.getUniqueId().toString()
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
