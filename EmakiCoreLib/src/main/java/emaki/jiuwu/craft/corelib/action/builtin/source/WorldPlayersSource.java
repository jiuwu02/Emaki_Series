package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;

public final class WorldPlayersSource extends BaseSource {

    public WorldPlayersSource() {
        super("world_players", "指定世界内的在线玩家。",
                CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.optional("world", CoreStageParameterType.STRING, "",
                        "世界名，留空取管道原点所在世界"),
                CoreStageParameter.optional("limit", CoreStageParameterType.INTEGER, "0",
                        "最大玩家数，0 表示不限制"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        World world;
        try {
            world = StageSupport.world(arguments.getString("world"), context.origin().getWorld());
        } catch (IllegalStateException exception) {
            return CoreSourceResult.empty("action.source.world_players.no_world");
        }
        if (world == null) {
            return CoreSourceResult.empty("action.source.world_players.no_world");
        }
        int limit = arguments.getInt("limit", 0);
        List<CoreActionSubject> subjects = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (limit > 0 && subjects.size() >= limit) {
                break;
            }
            subjects.add(CoreActionSubject.of(player));
        }
        if (subjects.isEmpty()) {
            return CoreSourceResult.empty("action.source.world_players.no_match");
        }
        return CoreSourceResult.selected(subjects);
    }
}
