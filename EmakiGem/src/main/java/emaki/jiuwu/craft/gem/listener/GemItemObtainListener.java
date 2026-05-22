package emaki.jiuwu.craft.gem.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;

public final class GemItemObtainListener implements Listener {

    private final EmakiGemPlugin plugin;

    public GemItemObtainListener(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            refreshLater(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshLater(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshLater(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeldChange(PlayerItemHeldEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        refreshLater(event.getPlayer());
    }

    private void refreshLater(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshInventory(player));
    }

    private void refreshInventory(Player player) {
        if (plugin.stateService() == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            ItemStack refreshed = refreshItem(player, current);
            if (refreshed != current) {
                inventory.setItem(slot, refreshed);
                changed = true;
            }
        }
        if (changed) {
            player.updateInventory();
        }
    }

    private ItemStack refreshItem(Player player, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return itemStack;
        }
        GemItemDefinition definition = plugin.stateService().resolveItemDefinition(itemStack);
        if (definition == null) {
            return itemStack;
        }
        ItemStack refreshed = plugin.stateService().applyInitialState(player, itemStack, definition);
        return refreshed == null ? itemStack : refreshed;
    }
}
