package emaki.jiuwu.craft.corelib.action.builtin.v2.source;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;

/**
 * Players around the pipeline origin.
 *
 * <p>Shares {@link NearbyFilter} with {@code nearby}; only the player predicate differs. {@code limit}
 * defaults to {@code 0}, meaning every match, because a party- or area-wide effect normally wants all
 * players rather than the closest one.</p>
 *
 * <p>Domain {@code LOCATION_REGION}: same region read as {@code nearby}.</p>
 */
public final class NearbyPlayersSource extends BaseSource {

    public NearbyPlayersSource() {
        super("nearby_players", "Players around the pipeline origin.",
                CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.optional("radius", CoreStageParameterType.DOUBLE, "1", "Search radius"),
                CoreStageParameter.optional("limit", CoreStageParameterType.INTEGER, "0",
                        "Maximum players, 0 for no limit"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Location centre;
        try {
            centre = context.origin();
        } catch (IllegalStateException exception) {
            return CoreSourceResult.empty("action.v2.source.nearby_players.no_origin");
        }
        if (centre == null || centre.getWorld() == null) {
            return CoreSourceResult.empty("action.v2.source.nearby_players.no_origin");
        }
        double radius = Math.max(0D, arguments.getDouble("radius", 1D));
        int configured = arguments.getInt("limit", 0);
        World world = centre.getWorld();
        List<Entity> candidates = List.copyOf(world.getNearbyEntities(centre, radius, radius, radius));
        int limit = configured <= 0 ? Math.max(1, candidates.size()) : configured;
        List<Entity> matches = NearbyFilter.apply(candidates, centre, null, true, limit, true);
        if (matches.isEmpty()) {
            return CoreSourceResult.empty("action.v2.source.nearby_players.no_match");
        }
        List<CoreActionSubject> subjects = new ArrayList<>(matches.size());
        matches.forEach(entity -> subjects.add(CoreActionSubject.of(entity)));
        return CoreSourceResult.selected(subjects);
    }
}
