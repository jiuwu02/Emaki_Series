package emaki.jiuwu.craft.codex.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;

/**
 * Handles per-player recipe sync on join and unlock-data persistence on quit.
 */
public final class PlayerConnectionListener implements Listener {

    private final EmakiCodexPlugin plugin;

    public PlayerConnectionListener(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.appConfig().recipeBridgeEnabled() || !plugin.appConfig().syncOnJoin()) {
            return;
        }
        plugin.recipeSyncGateway().sync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.unlockStore().saveAndClearAsync(event.getPlayer().getUniqueId());
    }
}
