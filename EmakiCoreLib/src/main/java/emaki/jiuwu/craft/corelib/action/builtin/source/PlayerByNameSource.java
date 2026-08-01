package emaki.jiuwu.craft.corelib.action.builtin.v2.source;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
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
            return CoreSourceResult.invalid("action.v2.source.player_by_name.name_required");
        }
        Player player = byUuid(raw);
        if (player == null) {
            player = Bukkit.getPlayerExact(raw);
        }
        if (player == null || !player.isOnline()) {
            return CoreSourceResult.empty("action.v2.source.player_by_name.not_online");
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
