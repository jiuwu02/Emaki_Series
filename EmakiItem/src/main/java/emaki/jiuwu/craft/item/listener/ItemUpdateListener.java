package emaki.jiuwu.craft.item.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemUpdateListener implements Listener {

    private final EmakiItemPlugin plugin;

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

    private void delayed(Player player, String trigger) {
        plugin.getServer().getScheduler().runTask(plugin, () -> refresh(player, trigger));
    }

    private int refresh(Player player, String trigger) {
        int changed = plugin.updateService().updatePlayerItems(player, trigger);
        return changed + plugin.setService().refreshEquippedSets(player, trigger);
    }
}
