package emaki.jiuwu.craft.item.service;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.session.PlayerSessionMap;

final class ItemRepairGuiStateManager {

    private final PlayerSessionMap<ItemRepairGuiSession> sessions = new PlayerSessionMap<>(ItemRepairGuiSession::player);

    public ItemRepairGuiSession get(Player player) {
        return sessions.get(player);
    }

    public void put(ItemRepairGuiSession session) {
        sessions.put(session);
    }

    public void remove(Player player) {
        sessions.remove(player);
    }

    public void clear() {
        sessions.clear();
    }
}
