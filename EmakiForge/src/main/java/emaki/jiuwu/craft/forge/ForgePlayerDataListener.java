package emaki.jiuwu.craft.forge;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class ForgePlayerDataListener implements Listener {

    private final EmakiForgePlugin plugin;

    ForgePlayerDataListener(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.recipeBookGuiService() != null) {
            plugin.recipeBookGuiService().removeRecipeBook(event.getPlayer());
        }
        plugin.playerDataStore().saveAndClearAsync(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        if (plugin.recipeBookGuiService() != null) {
            plugin.recipeBookGuiService().removeRecipeBook(event.getPlayer());
        }
        plugin.playerDataStore().saveAndClearAsync(event.getPlayer().getUniqueId());
    }
}
