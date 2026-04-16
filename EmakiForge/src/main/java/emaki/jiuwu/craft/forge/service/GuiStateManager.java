package emaki.jiuwu.craft.forge.service;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.session.PlayerSessionMap;

final class GuiStateManager {

    private final PlayerSessionMap<ForgeGuiSession> sessions = new PlayerSessionMap<>(ForgeGuiSession::player);

    public ForgeGuiSession get(Player player) {
        return sessions.get(player);
    }

    public void put(ForgeGuiSession session) {
        sessions.put(session);
    }

    public void remove(Player player) {
        sessions.remove(player);
    }

    public void remove(ForgeGuiSession session) {
        sessions.remove(session);
    }

    public void clear() {
        sessions.clear();
    }
}
