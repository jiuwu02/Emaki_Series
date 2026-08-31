package emaki.jiuwu.craft.codex.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;

public final class PlayerConnectionListener implements Listener {

    private static final long RESYNC_DELAY_TICKS = 10L;

    private final EmakiCodexPlugin plugin;

    public PlayerConnectionListener(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.codexStore().beginSessionAsync(player.getUniqueId(), player.getName())
                .thenAccept(codex -> {
                    if (codex != null) {
                        plugin.codexProviderRegistrar().resyncPlayer(player);
                    }
                });
        if (!plugin.appConfig().advancementEnabled()) {
            return;
        }
        plugin.executionDispatcher().runEntityLater(plugin, player, () -> {
            if (player.isOnline()) {
                plugin.advancementPacketGateway().resync(player);
            }
        }, () -> { }, RESYNC_DELAY_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.codexStore().unloadAsync(event.getPlayer().getUniqueId());
    }
}
