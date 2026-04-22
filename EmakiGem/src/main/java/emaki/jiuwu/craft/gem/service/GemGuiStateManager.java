package emaki.jiuwu.craft.gem.service;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.session.PlayerSessionMap;

final class GemGuiStateManager {

    private final PlayerSessionMap<GemPlayerGuiSession> sessions = new PlayerSessionMap<>(GemPlayerGuiSession::player);

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
    }

    public void remove(Player player) {
        sessions.remove(player);
    }

    public void remove(GemPlayerGuiSession session) {
        sessions.remove(session);
    }

    public void clear() {
        sessions.clear();
    }

    private <T extends GemPlayerGuiSession> T get(Player player, Class<T> type) {
        GemPlayerGuiSession session = sessions.get(player);
        return type != null && type.isInstance(session) ? type.cast(session) : null;
    }
}
