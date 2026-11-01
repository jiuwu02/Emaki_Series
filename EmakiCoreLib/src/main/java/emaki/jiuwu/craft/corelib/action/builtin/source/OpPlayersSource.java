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

public final class OpPlayersSource extends BaseSource {

    public OpPlayersSource() {
        super("op_players", "全服在线的 OP 玩家。",
                CoreActionExecutionDomain.SERVER_GLOBAL);
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        List<CoreActionSubject> subjects = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                subjects.add(CoreActionSubject.of(player));
            }
        }
        if (subjects.isEmpty()) {
            return CoreSourceResult.empty("action.source.op_players.no_match");
        }
        return CoreSourceResult.selected(subjects);
    }
}
