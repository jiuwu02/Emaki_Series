package emaki.jiuwu.craft.gem.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.service.GemRerollSessionService;

public final class GemPlayerLifecycleListener implements Listener {

    private final EmakiGemPlugin plugin;

    public GemPlayerLifecycleListener(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        terminateRerolls(event.getPlayer(), GemRerollSessionService.TerminationReason.PLAYER_QUIT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        terminateRerolls(event.getPlayer(), GemRerollSessionService.TerminationReason.PLAYER_KICK);
    }

    private void terminateRerolls(Player player, GemRerollSessionService.TerminationReason reason) {
        if (player == null || plugin.rerollSessionService() == null) {
            return;
        }
        plugin.rerollSessionService().abandon(player.getUniqueId(), reason);
    }
}
