package emaki.jiuwu.craft.storage.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.model.PlayerStorage;

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
