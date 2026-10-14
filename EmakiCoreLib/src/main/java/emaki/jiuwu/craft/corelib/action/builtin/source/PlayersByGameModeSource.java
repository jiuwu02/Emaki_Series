package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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

public final class PlayersByGameModeSource extends BaseSource {

    public PlayersByGameModeSource() {
        super("players_by_gamemode", "处于指定游戏模式的在线玩家。",
                CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("gamemode", CoreStageParameterType.STRING,
                        "游戏模式，如 survival / creative / adventure / spectator"),
                CoreStageParameter.optional("limit", CoreStageParameterType.INTEGER, "0",
                        "最大玩家数，0 表示不限制"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        String raw = Texts.lower(Texts.trim(arguments.getString("gamemode")));
        GameMode mode;
        try {
            mode = GameMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return CoreSourceResult.invalid("action.source.players_by_gamemode.unknown_gamemode",
                    java.util.Map.of("gamemode", raw));
        }
        int limit = Math.max(0, arguments.getInt("limit", 0));
        List<CoreActionSubject> subjects = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (limit > 0 && subjects.size() >= limit) {
                break;
            }
            if (player.getGameMode() == mode) {
                subjects.add(CoreActionSubject.of(player));
            }
        }
        if (subjects.isEmpty()) {
            return CoreSourceResult.empty("action.source.players_by_gamemode.no_match");
        }
        return CoreSourceResult.selected(subjects);
    }
}
