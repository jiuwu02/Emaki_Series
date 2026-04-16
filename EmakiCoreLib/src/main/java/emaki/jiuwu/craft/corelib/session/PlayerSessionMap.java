package emaki.jiuwu.craft.corelib.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.bukkit.entity.Player;

public final class PlayerSessionMap<S> {

    private final Map<UUID, S> sessions = new ConcurrentHashMap<>();
    private final Function<S, Player> playerExtractor;

    public PlayerSessionMap(Function<S, Player> playerExtractor) {
        this.playerExtractor = playerExtractor;
    }

    public S get(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public void put(S session) {
        Player player = player(session);
        if (player != null) {
            sessions.put(player.getUniqueId(), session);
        }
    }

    public void remove(Player player) {
        if (player != null) {
            sessions.remove(player.getUniqueId());
        }
    }

    public void remove(UUID playerId) {
        if (playerId != null) {
            sessions.remove(playerId);
        }
    }

    public boolean remove(S session) {
        Player player = player(session);
        return player != null && sessions.remove(player.getUniqueId(), session);
    }

    public boolean remove(Player player, S session) {
        return player != null && session != null && sessions.remove(player.getUniqueId(), session);
    }

    public void clear() {
        sessions.clear();
    }

    private Player player(S session) {
        return session == null || playerExtractor == null ? null : playerExtractor.apply(session);
    }
}
