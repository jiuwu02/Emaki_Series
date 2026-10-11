package emaki.jiuwu.craft.structure;

import emaki.jiuwu.craft.structure.selection.SelectionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class SelectionQuitListener implements Listener {

    private final EmakiStructurePlugin plugin;

    public SelectionQuitListener(EmakiStructurePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SelectionManager manager = plugin.selectionManager();
        if (manager == null) {
            return;
        }
        manager.handleQuit(event.getPlayer());
    }
}
