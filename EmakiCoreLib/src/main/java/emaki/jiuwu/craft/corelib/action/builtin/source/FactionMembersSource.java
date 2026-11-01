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
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class FactionMembersSource extends BaseSource {

    public FactionMembersSource() {
        super("faction_members", "指定阵营成员中的在线玩家。",
                CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("member", CoreStageParameterType.STRING,
                        "成员玩家名列表，逗号分隔"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        List<String> members = splitMembers(arguments.getString("member"));
        if (members.isEmpty()) {
            return CoreSourceResult.empty("action.source.faction_members.no_members");
        }
        List<CoreActionSubject> subjects = new ArrayList<>();
        for (String name : members) {
            Player player = Bukkit.getPlayerExact(name);
            if (player != null && player.isOnline()) {
                subjects.add(CoreActionSubject.of(player));
            }
        }
        if (subjects.isEmpty()) {
            return CoreSourceResult.empty("action.source.faction_members.no_online");
        }
        return CoreSourceResult.selected(subjects);
    }

    private static List<String> splitMembers(String raw) {
        List<String> names = new ArrayList<>();
        for (String part : Texts.toStringSafe(raw).split(",")) {
            String trimmed = Texts.trim(part);
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return names;
    }
}
