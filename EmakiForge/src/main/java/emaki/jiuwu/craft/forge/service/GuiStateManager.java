package emaki.jiuwu.craft.forge.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.session.PlayerSessionMap;

final class GuiStateManager {

    private final PlayerSessionMap<ForgeGuiSession> sessions = new PlayerSessionMap<>(ForgeGuiSession::player);
    private final Set<ForgeGuiSession> trackedSessions = ConcurrentHashMap.newKeySet();

    public ForgeGuiSession get(Player player) {
        return sessions.get(player);
    }

    public void put(ForgeGuiSession session) {
        if (session == null || session.player() == null) {
            return;
        }
        sessions.put(session);
        trackedSessions.add(session);
    }

    public boolean isCurrent(ForgeGuiSession session) {
        return session != null && session.player() != null && sessions.get(session.player()) == session;
    }

    public void remove(Player player) {
        ForgeGuiSession session = sessions.get(player);
        if (session != null) {
            remove(session);
        }
    }

    public void remove(ForgeGuiSession session) {
        sessions.remove(session);
        trackedSessions.remove(session);
    }

    public List<ForgeGuiSession> snapshot() {
        return List.copyOf(trackedSessions);
    }

    public void clearSettled() {
        for (ForgeGuiSession session : snapshot()) {
            if (session != null && session.settlementCommitted()) {
                remove(session);
            }
        }
    }

    public void clear() {
        sessions.clear();
        trackedSessions.clear();
    }
}
