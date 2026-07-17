package emaki.jiuwu.craft.gem.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.session.PlayerSessionMap;

final class GemGuiStateManager {

    private final PlayerSessionMap<GemPlayerGuiSession> sessions = new PlayerSessionMap<>(GemPlayerGuiSession::player);
    private final Map<UUID, Player> viewers = new ConcurrentHashMap<>();

    public GemGuiSession getGem(Player player) {
        return get(player, GemGuiSession.class);
    }

    public GemOpenGuiSession getOpen(Player player) {
        return get(player, GemOpenGuiSession.class);
    }

    public GemUpgradeGuiSession getUpgrade(Player player) {
        return get(player, GemUpgradeGuiSession.class);
    }

    public void put(GemPlayerGuiSession session) {
        sessions.put(session);
        if (session != null && session.player() != null) {
            viewers.put(session.player().getUniqueId(), session.player());
        }
    }

    public void remove(Player player) {
        sessions.remove(player);
        if (player != null) {
            viewers.remove(player.getUniqueId(), player);
        }
    }

    public List<Player> viewers() {
        return List.copyOf(viewers.values());
    }

    public void remove(GemPlayerGuiSession session) {
        sessions.remove(session);
        if (session != null && session.player() != null) {
            viewers.remove(session.player().getUniqueId(), session.player());
        }
    }

    public void clear() {
        sessions.clear();
        viewers.clear();
    }

    private <T extends GemPlayerGuiSession> T get(Player player, Class<T> type) {
        GemPlayerGuiSession session = sessions.get(player);
        return type != null && type.isInstance(session) ? type.cast(session) : null;
    }
}
