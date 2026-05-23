package emaki.jiuwu.craft.attribute.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import emaki.jiuwu.craft.attribute.service.AttributeService;

public final class InventoryInteractionListener implements Listener {

    private final AttributeService attributeService;

    public InventoryInteractionListener(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @EventHandler
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        attributeService.scheduleEquipmentSync(event.getPlayer());
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        attributeService.scheduleEquipmentSync(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            attributeService.scheduleEquipmentSync(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            attributeService.scheduleEquipmentSync(player);
        }
    }
}
