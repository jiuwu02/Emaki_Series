package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

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
            return CoreSourceResult.empty("action.source.nearby_players.no_origin");
        }
        if (centre == null || centre.getWorld() == null) {
            return CoreSourceResult.empty("action.source.nearby_players.no_origin");
        }
        double radius = Math.max(0D, arguments.getDouble("radius", 1D));
        int configured = arguments.getInt("limit", 0);
        World world = centre.getWorld();
        List<Entity> candidates = List.copyOf(world.getNearbyEntities(centre, radius, radius, radius));
        int limit = configured <= 0 ? Math.max(1, candidates.size()) : configured;
        List<Entity> matches = NearbyFilter.apply(candidates, centre, null, true, limit, true);
        if (matches.isEmpty()) {
            return CoreSourceResult.empty("action.source.nearby_players.no_match");
        }
        List<CoreActionSubject> subjects = new ArrayList<>(matches.size());
        matches.forEach(entity -> subjects.add(CoreActionSubject.of(entity)));
        return CoreSourceResult.selected(subjects);
    }
}
