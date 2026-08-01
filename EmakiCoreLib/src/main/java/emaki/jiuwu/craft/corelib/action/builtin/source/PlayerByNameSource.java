package emaki.jiuwu.craft.corelib.action.builtin.source;

import java.util.List;
import java.util.UUID;

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
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * An online player, addressed by name or UUID.
 *
 * <p>Holds the name-or-UUID resolution that Cooking previously reinvented in its own execution-target
 * helper, so the pipeline text is the only place a player has to be named.</p>
 *
 * <p>Domain {@code SERVER_GLOBAL}: {@code Bukkit.getPlayer} reads the server's online-player table, which
 * is global state rather than per-entity state.</p>
 */
public final class PlayerByNameSource extends BaseSource {

    public PlayerByNameSource() {
        super("player_by_name", "An online player, addressed by name or UUID.",
                CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.positional("name", CoreStageParameterType.STRING, "Player name or UUID"));
    }

    @Override
    public @NotNull CoreSourceResult select(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        String raw = Texts.trim(arguments.getString("name"));
        if (raw.isEmpty()) {
            return CoreSourceResult.invalid("action.source.player_by_name.name_required");
        }
        Player player = byUuid(raw);
        if (player == null) {
            player = Bukkit.getPlayerExact(raw);
        }
        if (player == null || !player.isOnline()) {
            return CoreSourceResult.empty("action.source.player_by_name.not_online");
        }
        return CoreSourceResult.selected(List.of(CoreActionSubject.of(player)));
    }

    private static Player byUuid(String raw) {
        if (raw.length() != 36 || raw.indexOf('-') < 0) {
            return null;
        }
        try {
            return Bukkit.getPlayer(UUID.fromString(raw));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
