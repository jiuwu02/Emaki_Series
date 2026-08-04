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

/**
 * Loads and unloads queue data around a player's session.
 *
 * <p>Join settles nothing directly. Entries that came due while the owner was away are picked up by the
 * ticker once their data is loaded, which keeps settlement on one path instead of two.
 *
 * <p>Quit freezes online progress before writing, so an entry saved mid-craft cannot have its live tick
 * timestamp reinterpreted as elapsed time after a restart.
 */
public final class StationPlayerListener implements Listener {

    private final EmakiStationPlugin plugin;

    /**
     * Creates the listener.
     *
     * @param plugin the owning plugin
     */
    public StationPlayerListener(EmakiStationPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads the joining player's queues and resumes their online progress.
     *
     * @param event the join event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.queueService().loadAsync(player.getUniqueId()).thenAccept(queues -> {
            if (queues == null) {
                return;
            }
            plugin.executionDispatcher().runEntity(plugin, player,
                    () -> resumeOnlineQueues(queues), () -> {
                        // The player left again before their data could be applied. The next join reloads it.
                    });
        });
    }

    /**
     * Closes the leaving player's window, freezes progress, and flushes their queues.
     *
     * @param event the quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Closing first returns any items the player left in the input slots.
        plugin.stationGuiService().close(player.getUniqueId());
        plugin.queueService().unloadAsync(player.getUniqueId());
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
