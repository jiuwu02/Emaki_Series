package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

public final class NearbySource extends BaseSource {

    public NearbySource() {
        super("nearby", "Entities around the pipeline origin.",
                CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.optional("radius", CoreStageParameterType.DOUBLE, "1", "Search radius"),
                CoreStageParameter.optional("limit", CoreStageParameterType.INTEGER, "1", "Maximum entities"),
                CoreStageParameter.optional("type", CoreStageParameterType.ENTITY_TYPE, "", "Entity type filter"),
                CoreStageParameter.optional("include_players", CoreStageParameterType.BOOLEAN, "false",
                        "Allow player targets"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        EntityType type = null;
        if (arguments.has("type")) {
            type = arguments.getEntityType("type").orElse(null);
            if (type == null) {
                return CoreSourceResult.invalid("action.source.nearby.unknown_entity_type",
                        Map.of("type", arguments.getString("type")));
            }
        }
        Location centre = centre(context);
        if (centre == null || centre.getWorld() == null) {
            return CoreSourceResult.empty("action.source.nearby.no_origin");
        }
        double radius = Math.max(0D, arguments.getDouble("radius", 1D));
        int limit = Math.max(1, arguments.getInt("limit", 1));
        boolean includePlayers = arguments.getBoolean("include_players", false);
        World world = centre.getWorld();
        List<Entity> matches = NearbyFilter.apply(
                List.copyOf(world.getNearbyEntities(centre, radius, radius, radius)),
                centre, type, includePlayers, limit, false);
        if (matches.isEmpty()) {
            return CoreSourceResult.empty("action.source.nearby.no_match");
        }
        List<CoreActionSubject> subjects = new ArrayList<>(matches.size());
        matches.forEach(entity -> subjects.add(CoreActionSubject.of(entity)));
        return CoreSourceResult.selected(subjects);
    }

    private static Location centre(CoreStageContext context) {
        try {
            return context.origin();
        } catch (IllegalStateException exception) {
            return null;
        }
    }
}
