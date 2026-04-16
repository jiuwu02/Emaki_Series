package emaki.jiuwu.craft.strengthen.service;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.session.PlayerSessionMap;

final class StrengthenGuiStateManager {

    private final PlayerSessionMap<StrengthenGuiSession> sessions = new PlayerSessionMap<>(StrengthenGuiSession::player);

    public StrengthenGuiSession get(Player player) {
        return sessions.get(player);
    }

    public void put(StrengthenGuiSession session) {
        sessions.put(session);
    }

    public void remove(Player player) {
        sessions.remove(player);
    }

    public void clear() {
        sessions.clear();
    }
}
