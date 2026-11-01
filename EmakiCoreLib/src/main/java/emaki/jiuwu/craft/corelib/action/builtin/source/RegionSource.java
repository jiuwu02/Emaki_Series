package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

public final class RegionSource extends BaseSource {

    public RegionSource() {
        super("region", "以管道原点为角点的立方区域实体。",
                CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.optional("width", CoreStageParameterType.DOUBLE, "8", "X 轴半宽"),
                CoreStageParameter.optional("height", CoreStageParameterType.DOUBLE, "8", "Y 轴半高"),
                CoreStageParameter.optional("depth", CoreStageParameterType.DOUBLE, "8", "Z 轴半深"),
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
            return CoreSourceResult.empty("action.source.region.no_origin");
        }
        if (centre == null || centre.getWorld() == null) {
            return CoreSourceResult.empty("action.source.region.no_origin");
        }
        double width = Math.max(0D, arguments.getDouble("width", 8D));
        double height = Math.max(0D, arguments.getDouble("height", 8D));
        double depth = Math.max(0D, arguments.getDouble("depth", 8D));
        int limit = Math.max(0, arguments.getInt("limit", 0));
        boolean includePlayers = arguments.getBoolean("include_players", true);
        EntityType type = null;
        if (arguments.has("type")) {
            type = arguments.getEntityType("type").orElse(null);
            if (type == null) {
                return CoreSourceResult.invalid("action.source.region.unknown_entity_type",
                        Map.of("type", arguments.getString("type")));
            }
        }
        World world = centre.getWorld();
        List<Entity> candidates = List.copyOf(world.getNearbyEntities(centre, width, height, depth));
        List<CoreActionSubject> subjects = new ArrayList<>();
        for (Entity entity : candidates) {
            if (limit > 0 && subjects.size() >= limit) {
                break;
            }
            if (!includePlayers && entity instanceof Player) {
                continue;
            }
            if (type != null && entity.getType() != type) {
                continue;
            }
            subjects.add(CoreActionSubject.of(entity));
        }
        if (subjects.isEmpty()) {
            return CoreSourceResult.empty("action.source.region.no_match");
        }
        return CoreSourceResult.selected(subjects);
    }
}
