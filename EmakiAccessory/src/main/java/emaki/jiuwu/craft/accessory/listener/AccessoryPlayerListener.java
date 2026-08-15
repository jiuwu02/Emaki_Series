package emaki.jiuwu.craft.accessory.listener;

import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.EmakiAccessoryPlugin;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;

public final class AccessoryPlayerListener implements Listener {

    private final EmakiAccessoryPlugin plugin;

    public AccessoryPlayerListener(EmakiAccessoryPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        plugin.accessoryStore().beginSessionAsync(playerId, player.getName())
                .thenAccept(accessories -> {
                    if (accessories == null || plugin.isShutdownStarted()) {
                        return;
                    }

                    plugin.executionDispatcher().runEntity(plugin, player,
                            () -> plugin.refreshContributions(accessories), () -> {

                            });
                });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        plugin.writeSessions().releaseAllHeldBy(playerId);
        plugin.writeSessions().release(playerId, playerId);
        plugin.setService().forget(playerId);
        plugin.contributionService().invalidate(playerId);
        plugin.accessoryStore().unloadAsync(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.appConfig().dropOnDeath() || event.getKeepInventory()) {
            return;
        }
        Player player = event.getEntity();
        Map<String, ItemStack> dropped = plugin.accessoryStore()
                .mutate(player.getUniqueId(), 0L, PlayerAccessories::clearAll);
        if (dropped == null || dropped.isEmpty()) {
            return;
        }
        event.getDrops().addAll(dropped.values());
        PlayerAccessories accessories = plugin.accessoryStore().cached(player.getUniqueId());
        if (accessories != null) {
            plugin.refreshContributions(accessories);
        }
        plugin.accessoryStore().saveAsync(player.getUniqueId());
    }
}
