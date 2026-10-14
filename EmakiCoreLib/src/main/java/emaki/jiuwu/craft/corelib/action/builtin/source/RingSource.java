package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.List;

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
import emaki.jiuwu.craft.corelib.api.text.Texts;

import java.util.Map;

public final class RingSource extends BaseSource {

    public RingSource() {
        super("ring", "位于内半径与外半径之间的环形区域实体。",
                CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.optional("min_radius", CoreStageParameterType.DOUBLE, "1", "内半径"),
                CoreStageParameter.optional("max_radius", CoreStageParameterType.DOUBLE, "8", "外半径"),
                CoreStageParameter.optional("limit", CoreStageParameterType.INTEGER, "0",
                        "最大实体数，0 表示不限制"),
                CoreStageParameter.optional("type", CoreStageParameterType.ENTITY_TYPE, "", "实体类型过滤"),
                CoreStageParameter.optional("include_players", CoreStageParameterType.BOOLEAN, "true",
                        "允许以玩家为目标"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Location centre;
        try {
            centre = context.origin();
        } catch (IllegalStateException exception) {
            return CoreSourceResult.empty("action.source.ring.no_origin");
        }
        if (centre == null || centre.getWorld() == null) {
            return CoreSourceResult.empty("action.source.ring.no_origin");
        }
        double minRadius = Math.max(0D, arguments.getDouble("min_radius", 1D));
        double maxRadius = Math.max(minRadius, arguments.getDouble("max_radius", 8D));
        int limit = Math.max(0, arguments.getInt("limit", 0));
        boolean includePlayers = arguments.getBoolean("include_players", true);
        EntityType type = null;
        if (arguments.has("type")) {
            type = arguments.getEntityType("type").orElse(null);
            if (type == null) {
                return CoreSourceResult.invalid("action.source.ring.unknown_entity_type",
                        Map.of("type", arguments.getString("type")));
            }
        }
        World world = centre.getWorld();
        List<Entity> candidates = List.copyOf(world.getNearbyEntities(centre, maxRadius, maxRadius, maxRadius));
        List<CoreActionSubject> subjects = new ArrayList<>();
        for (Entity entity : candidates) {
            if (limit > 0 && subjects.size() >= limit) {
                break;
            }
            if (!includePlayers && entity instanceof org.bukkit.entity.Player) {
                continue;
            }
            if (type != null && entity.getType() != type) {
                continue;
            }
            double distance = entity.getLocation().distance(centre);
            if (distance < minRadius || distance > maxRadius) {
                continue;
            }
            subjects.add(CoreActionSubject.of(entity));
        }
        if (subjects.isEmpty()) {
            return CoreSourceResult.empty("action.source.ring.no_match");
        }
        return CoreSourceResult.selected(subjects);
    }
}
