package emaki.jiuwu.craft.item.listener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemUpdateListener implements Listener {

    private final EmakiItemPlugin plugin;
    // 去重：同一 tick 内对同一玩家只调度一次延迟刷新
    private final Set<UUID> pendingRefresh = ConcurrentHashMap.newKeySet();

    public ItemUpdateListener(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        delayed(event.getPlayer(), "join");
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        delayed(event.getPlayer(), "held_change");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            delayed(player, "inventory_click");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            delayed(player, "inventory_drag");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            delayed(player, "pickup");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        refresh(event.getPlayer(), "interact");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingRefresh.remove(event.getPlayer().getUniqueId());
    }

    private void delayed(Player player, String trigger) {
        // 如果该玩家已有 pending 刷新任务，跳过重复调度
        if (!pendingRefresh.add(player.getUniqueId())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingRefresh.remove(player.getUniqueId());
            if (player.isOnline()) {
                refresh(player, trigger);
            }
        });
    }

    private int refresh(Player player, String trigger) {
        int changed = plugin.updateService().updatePlayerItems(player, trigger);
        return changed + plugin.setService().refreshEquippedSets(player, trigger);
    }
}
