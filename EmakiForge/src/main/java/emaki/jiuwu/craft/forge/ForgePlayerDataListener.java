package emaki.jiuwu.craft.forge;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

final class ForgePlayerDataListener implements Listener {

    private final EmakiForgePlugin plugin;

    ForgePlayerDataListener(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.playerDataStore().save(event.getPlayer().getUniqueId());
        plugin.playerDataStore().clear(event.getPlayer().getUniqueId());
    }
}
