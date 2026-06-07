package emaki.jiuwu.craft.cooking;

import dev.lone.itemsadder.api.CustomComplexFurniture;
import dev.lone.itemsadder.api.Events.ComplexFurnitureBreakEvent;
import dev.lone.itemsadder.api.Events.ComplexFurnitureInteractEvent;
import dev.lone.itemsadder.api.Events.CustomBlockBreakEvent;
import dev.lone.itemsadder.api.Events.CustomBlockInteractEvent;
import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import dev.lone.itemsadder.api.Events.FurnitureInteractEvent;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceType;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

final class ItemsAdderCookingStationListener implements Listener {

    private final CookingStationListener stationListener;

    ItemsAdderCookingStationListener(CookingStationListener stationListener) {
        this.stationListener = stationListener;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemsAdderInteract(CustomBlockInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockClicked();
        if (player == null || block == null) {
            return;
        }
        Action action = event.getAction();
        ItemSource source = itemsAdderSource(event.getNamespacedID());
        stationListener.dispatchInteraction(new StationInteraction(
                player,
                block,
                action == Action.LEFT_CLICK_BLOCK,
                action == Action.RIGHT_CLICK_BLOCK,
                event.getHand() == EquipmentSlot.HAND,
                event::setCancelled,
                source
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemsAdderBreak(CustomBlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player == null || block == null) {
            return;
        }
        ItemSource source = itemsAdderSource(event.getNamespacedID());
        stationListener.dispatchBreak(new StationBreakContext(
                player,
                block,
                event::setCancelled,
                source
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemsAdderFurnitureInteract(FurnitureInteractEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getBukkitEntity();
        ItemSource source = itemsAdderSource(event.getNamespacedID());
        dispatchFurnitureInteraction(player, entity, event.getAction(), event::setCancelled, source);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemsAdderFurnitureBreak(FurnitureBreakEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getBukkitEntity();
        ItemSource source = itemsAdderSource(event.getNamespacedID());
        dispatchFurnitureBreak(player, entity, event::setCancelled, source);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemsAdderComplexFurnitureInteract(ComplexFurnitureInteractEvent event) {
        CustomComplexFurniture furniture = event.getFurniture();
        Entity entity = furniture == null ? null : furniture.getEntity();
        ItemSource source = itemsAdderSource(event.getNamespacedID());
        dispatchFurnitureInteraction(event.getPlayer(), entity, event.getAction(), event::setCancelled, source);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemsAdderComplexFurnitureBreak(ComplexFurnitureBreakEvent event) {
        CustomComplexFurniture furniture = event.getFurniture();
        Entity entity = furniture == null ? null : furniture.getEntity();
        ItemSource source = itemsAdderSource(event.getNamespacedID());
        dispatchFurnitureBreak(event.getPlayer(), entity, event::setCancelled, source);
    }

    private void dispatchFurnitureInteraction(Player player,
            Entity entity,
            Action action,
            java.util.function.Consumer<Boolean> cancelConsumer,
            ItemSource source) {
        Block block = anchorBlock(entity);
        if (player == null || block == null || source == null) {
            return;
        }
        stationListener.dispatchInteraction(new StationInteraction(
                player,
                block,
                isLeftClick(action),
                isRightClick(action),
                true,
                cancelConsumer,
                source
        ));
    }

    private void dispatchFurnitureBreak(Player player,
            Entity entity,
            java.util.function.Consumer<Boolean> cancelConsumer,
            ItemSource source) {
        Block block = anchorBlock(entity);
        if (block == null || source == null) {
            return;
        }
        stationListener.dispatchBreak(new StationBreakContext(
                player,
                block,
                cancelConsumer,
                source
        ));
    }

    private Block anchorBlock(Entity entity) {
        return entity == null || entity.getWorld() == null ? null : entity.getLocation().getBlock();
    }

    private boolean isLeftClick(Action action) {
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private ItemSource itemsAdderSource(String namespacedId) {
        return namespacedId == null || namespacedId.isBlank() ? null : new ItemSource(ItemSourceType.ITEMSADDER, namespacedId);
    }
}
