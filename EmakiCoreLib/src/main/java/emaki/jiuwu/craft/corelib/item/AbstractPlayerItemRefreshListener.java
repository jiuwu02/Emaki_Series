package emaki.jiuwu.craft.corelib.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public abstract class AbstractPlayerItemRefreshListener implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, BukkitTask> scheduledRefreshes = new HashMap<>();

    protected AbstractPlayerItemRefreshListener(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    protected abstract PlayerItemRefreshService refreshService();

    @EventHandler(priority = EventPriority.MONITOR)
    public final void onJoin(PlayerJoinEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onDrop(PlayerDropItemEvent event) {
        PlayerItemRefreshService refreshService = refreshService();
        if (refreshService != null) {
            refreshService.refreshDroppedItem(event.getItemDrop());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerItemRefreshService refreshService = refreshService();
        if (refreshService != null) {
            refreshService.refreshDroppedItem(event.getItem());
        }
        scheduleRefresh(player);
    }

    @EventHandler
    public final void onQuit(PlayerQuitEvent event) {
        BukkitTask task = scheduledRefreshes.remove(event.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    protected final void scheduleRefresh(Player player) {
        if (player == null || refreshService() == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (scheduledRefreshes.containsKey(playerId)) {
            return;
        }
        BukkitTask task = plugin.getServer().getScheduler().runTask(plugin, () -> {
            scheduledRefreshes.remove(playerId);
            if (!player.isOnline()) {
                return;
            }
            PlayerItemRefreshService refreshService = refreshService();
            if (refreshService != null) {
                refreshService.refreshPlayerInventory(player);
            }
        });
        scheduledRefreshes.put(playerId, task);
    }
}
