package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
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

public final class AllPlayersSource extends BaseSource {

    public AllPlayersSource() {
        super("all_players", "全服在线玩家。",
                CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.optional("limit", CoreStageParameterType.INTEGER, "0",
                        "最大玩家数，0 表示不限制"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        int limit = arguments.getInt("limit", 0);
        List<CoreActionSubject> subjects = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (limit > 0 && subjects.size() >= limit) {
                break;
            }
            subjects.add(CoreActionSubject.of(player));
        }
        if (subjects.isEmpty()) {
            return CoreSourceResult.empty("action.source.all_players.no_match");
        }
        return CoreSourceResult.selected(subjects);
    }
}
