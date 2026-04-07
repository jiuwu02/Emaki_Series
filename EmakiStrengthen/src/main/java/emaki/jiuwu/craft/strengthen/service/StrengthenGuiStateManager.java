package emaki.jiuwu.craft.strengthen.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

final class StrengthenGuiStateManager {

    private final Map<UUID, StrengthenGuiSession> sessions = new LinkedHashMap<>();

    public StrengthenGuiSession get(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public void put(StrengthenGuiSession session) {
        if (session == null || session.player() == null) {
            return;
        }
        sessions.put(session.player().getUniqueId(), session);
    }

    public void remove(Player player) {
        if (player != null) {
            sessions.remove(player.getUniqueId());
        }
    }

    public void clear() {
        sessions.clear();
    }
}
