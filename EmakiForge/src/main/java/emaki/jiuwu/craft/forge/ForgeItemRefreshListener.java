package emaki.jiuwu.craft.forge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

final class ForgeItemRefreshListener implements Listener {

    private final EmakiForgePlugin plugin;
    private final Map<UUID, BukkitTask> scheduledRefreshes = new HashMap<>();

    ForgeItemRefreshListener(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.itemRefreshService() != null) {
            plugin.itemRefreshService().refreshDroppedItem(event.getItemDrop());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.itemRefreshService() != null) {
            plugin.itemRefreshService().refreshDroppedItem(event.getItem());
        }
        scheduleRefresh(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BukkitTask task = scheduledRefreshes.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void scheduleRefresh(Player player) {
        if (player == null || plugin.itemRefreshService() == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (scheduledRefreshes.containsKey(playerId)) {
            return;
        }
        BukkitTask task = plugin.getServer().getScheduler().runTask(plugin, () -> {
            scheduledRefreshes.remove(playerId);
            if (player.isOnline() && plugin.itemRefreshService() != null) {
                plugin.itemRefreshService().refreshPlayerInventory(player);
            }
        });
        scheduledRefreshes.put(playerId, task);
    }
}
