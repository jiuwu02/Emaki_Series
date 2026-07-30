package emaki.jiuwu.craft.storage.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.model.PlayerStorage;

/**
 * Loads a player's storage on join and flushes it on quit.
 *
 * <p>The load itself happens on the async file lane; only the generation re-check runs on the
 * player's owner thread. That re-check is what makes a stale load harmless: if the player
 * disconnected and reconnected while the read was in flight, the newer session already bumped the
 * generation and the old result is dropped rather than installed over it.
 */
public final class StoragePlayerListener implements Listener {

    private final EmakiStoragePlugin plugin;

    public StoragePlayerListener(EmakiStoragePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long generation = plugin.dataStore().currentGeneration(player.getUniqueId()) + 1L;
        plugin.dataStore().beginSessionAsync(player.getUniqueId(), player.getName())
                .whenComplete((loaded, throwable) -> {
                    if (throwable != null) {
                        plugin.getLogger().warning("[storage] Failed to load storage for "
                                + player.getName() + ": " + throwable.getClass().getSimpleName());
                        return;
                    }
                    if (loaded == null) {
                        return;
                    }
                    plugin.executionDispatcher().runEntity(plugin, player,
                            () -> applyLoaded(player, loaded, generation),
                            () -> {
                            });
                });
    }

    private void applyLoaded(Player player, PlayerStorage loaded, long expectedGeneration) {
        if (!player.isOnline()) {
            return;
        }
        if (!plugin.dataStore().isCurrentGeneration(player.getUniqueId(), expectedGeneration)) {
            return;
        }
        loaded.playerName(player.getName());
        // Capacity depends on the player's permission tier, which is only readable once they are
        // online, so overflow is evaluated here rather than during the async read.
        var capacity = plugin.capacityService().capacityOf(loaded, player,
                plugin.storageGuiService().slotsPerPage());
        plugin.overflowService().evaluate(loaded, player, capacity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.dataStore().unloadAsync(event.getPlayer().getUniqueId());
        plugin.storageGuiService().releaseViewState(event.getPlayer().getUniqueId());
    }
}
