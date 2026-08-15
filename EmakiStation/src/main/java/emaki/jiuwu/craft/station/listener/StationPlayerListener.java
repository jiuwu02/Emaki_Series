package emaki.jiuwu.craft.station.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.station.EmakiStationPlugin;
import emaki.jiuwu.craft.station.api.model.ProgressMode;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.queue.CraftQueue;
import emaki.jiuwu.craft.station.queue.PlayerQueues;

public final class StationPlayerListener implements Listener {

    private final EmakiStationPlugin plugin;

    public StationPlayerListener(EmakiStationPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.queueUnlockService() != null) {
            plugin.queueUnlockService().loadAsync(player.getUniqueId());
        }
        plugin.queueService().loadAsync(player.getUniqueId()).thenAccept(queues -> {
            if (queues == null) {
                return;
            }
            plugin.executionDispatcher().runEntity(plugin, player,
                    () -> resumeOnlineQueues(queues), () -> {

                    });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        plugin.stationGuiService().close(player.getUniqueId());
        plugin.queueService().unloadAsync(player.getUniqueId());
        if (plugin.queueUnlockService() != null) {
            plugin.queueUnlockService().unloadAsync(player.getUniqueId());
        }
    }

    private void resumeOnlineQueues(PlayerQueues queues) {
        long now = System.currentTimeMillis();
        for (CraftQueue queue : queues.all()) {
            StationDefinition station = plugin.registry().station(queue.stationId());
            if (station != null && station.progressMode() == ProgressMode.ONLINE) {
                queue.resumeAll(now);
            }
        }
    }
}
