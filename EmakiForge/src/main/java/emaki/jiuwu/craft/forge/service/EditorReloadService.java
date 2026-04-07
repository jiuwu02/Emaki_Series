package emaki.jiuwu.craft.forge.service;

import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.forge.EmakiForgePlugin;

final class EditorReloadService {

    private final EmakiForgePlugin plugin;
    private final EditorStateManager stateManager;

    EditorReloadService(EmakiForgePlugin plugin, EditorStateManager stateManager) {
        this.plugin = plugin;
        this.stateManager = stateManager;
    }

    void reloadResources() {
        closeAllEditorSessions();
        plugin.reloadPluginState(true);
    }

    void closeAllEditorSessions() {
        for (Map.Entry<java.util.UUID, EditorSession> entry : stateManager.all().entrySet()) {
            EditorSession session = entry.getValue();
            if (session == null) {
                continue;
            }
            session.setClosingByService(true);
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        stateManager.clear();
    }
}
