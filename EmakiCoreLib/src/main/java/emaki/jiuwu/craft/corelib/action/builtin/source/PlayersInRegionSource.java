package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
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

public final class PlayersInRegionSource extends BaseSource {

    public PlayersInRegionSource() {
        super("players_in_region", "以管道原点为中心的立方区域内的玩家。",
                CoreActionExecutionDomain.LOCATION_REGION,
                CoreStageParameter.optional("width", CoreStageParameterType.DOUBLE, "16", "X 轴半宽"),
                CoreStageParameter.optional("height", CoreStageParameterType.DOUBLE, "16", "Y 轴半高"),
                CoreStageParameter.optional("depth", CoreStageParameterType.DOUBLE, "16", "Z 轴半深"),
                CoreStageParameter.optional("limit", CoreStageParameterType.INTEGER, "0",
                        "最大玩家数，0 表示不限制"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Location centre;
        try {
            centre = context.origin();
        } catch (IllegalStateException exception) {
            return CoreSourceResult.empty("action.source.players_in_region.no_origin");
        }
        if (centre == null || centre.getWorld() == null) {
            return CoreSourceResult.empty("action.source.players_in_region.no_origin");
        }
        double width = Math.max(0D, arguments.getDouble("width", 16D));
        double height = Math.max(0D, arguments.getDouble("height", 16D));
        double depth = Math.max(0D, arguments.getDouble("depth", 16D));
        int limit = Math.max(0, arguments.getInt("limit", 0));
        World world = centre.getWorld();
        List<CoreActionSubject> subjects = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (limit > 0 && subjects.size() >= limit) {
                break;
            }
            Location location = player.getLocation();
            if (Math.abs(location.getX() - centre.getX()) > width
                    || Math.abs(location.getY() - centre.getY()) > height
                    || Math.abs(location.getZ() - centre.getZ()) > depth) {
                continue;
            }
            subjects.add(CoreActionSubject.of(player));
        }
        if (subjects.isEmpty()) {
            return CoreSourceResult.empty("action.source.players_in_region.no_match");
        }
        return CoreSourceResult.selected(subjects);
    }
}
