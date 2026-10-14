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

public final class PartySource extends BaseSource {

    public PartySource() {
        super("party", "拥有同一队伍权限节点的在线玩家。",
                CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("permission", CoreStageParameterType.STRING,
                        "队伍权限节点，如 emaki.party.alpha"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        String node = arguments.getString("permission");
        if (node.isBlank()) {
            return CoreSourceResult.empty("action.source.party.no_permission");
        }
        List<CoreActionSubject> subjects = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(node)) {
                subjects.add(CoreActionSubject.of(player));
            }
        }
        if (subjects.isEmpty()) {
            return CoreSourceResult.empty("action.source.party.no_match");
        }
        return CoreSourceResult.selected(subjects);
    }
}
