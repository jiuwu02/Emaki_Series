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

/**
 * Loads and releases accessory sessions, and applies the death rule.
 *
 * <p>Join loads asynchronously and then recomputes contributions on the player's owner thread. The
 * recompute is the reason nothing else needs an invalidation hook: EmakiAttribute folds this module's
 * contributions into its own cache signature, so publishing once after load is enough for the rest of
 * the session.
 */
public final class AccessoryPlayerListener implements Listener {

    private final EmakiAccessoryPlugin plugin;

    /**
     * Creates the listener.
     *
     * @param plugin the owning plugin
     */
    public AccessoryPlayerListener(EmakiAccessoryPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads the joining player's accessories.
     *
     * @param event the join event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        plugin.accessoryStore().beginSessionAsync(playerId, player.getName())
                .thenAccept(accessories -> {
                    if (accessories == null || plugin.isShutdownStarted()) {
                        return;
                    }
                    // The attribute parser caches into item PDC, so the recompute has to run on the thread
                    // that owns the player rather than on the loader's completion thread.
                    plugin.executionDispatcher().runEntity(plugin, player,
                            () -> plugin.refreshContributions(accessories), () -> {
                                // Player left before the recompute ran; their unload persists the payload.
                            });
                });
    }

    /**
     * Releases the leaving player's session.
     *
     * @param event the quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        plugin.writeSessions().releaseAllHeldBy(playerId);
        plugin.writeSessions().release(playerId, playerId);
        plugin.setService().forget(playerId);
        plugin.contributionService().invalidate(playerId);
        plugin.accessoryStore().unloadAsync(playerId);
    }

    /**
     * Applies the configured death rule.
     *
     * <p>Runs at {@code MONITOR} so keep-inventory plugins have already settled the drop list. Items are
     * appended to the event's drops rather than placed in the inventory, so the vanilla death pipeline
     * handles them and no duplicate can survive in both places.
     *
     * @param event the death event
     */
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
