package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

final class GuiStateManager {

    private final Map<UUID, ForgeGuiSession> sessions = new LinkedHashMap<>();

    public ForgeGuiSession get(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public void put(ForgeGuiSession session) {
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

    public void remove(ForgeGuiSession session) {
        if (session != null && session.player() != null) {
            sessions.remove(session.player().getUniqueId());
        }
    }

    public void clear() {
        sessions.clear();
    }
}
