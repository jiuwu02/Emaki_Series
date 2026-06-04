package emaki.jiuwu.craft.level.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.level.EmakiLevelPlugin;

public final class PlayerDataListener implements Listener {

    private final EmakiLevelPlugin plugin;

    public PlayerDataListener(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.dataStore().load(event.getPlayer(), plugin.typeRegistry().asMap());
        plugin.levelService().syncAllOnline();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.dataStore().unload(event.getPlayer().getUniqueId(), true);
    }
}
