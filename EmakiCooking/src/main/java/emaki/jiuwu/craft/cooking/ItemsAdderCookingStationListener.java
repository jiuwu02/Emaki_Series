package emaki.jiuwu.craft.cooking;

import dev.lone.itemsadder.api.Events.CustomBlockBreakEvent;
import dev.lone.itemsadder.api.Events.CustomBlockInteractEvent;
import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

/**
 * ItemsAdder custom-block events. Furniture events (simple and complex) are handled by CoreLib's
 * furniture relay; see {@code CookingFurnitureEventSubscriber}.
 */
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
        ItemSourceRef source = itemsAdderSource(event.getNamespacedID());
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
        ItemSourceRef source = itemsAdderSource(event.getNamespacedID());
        stationListener.dispatchBreak(new StationBreakContext(
                player,
                block,
                event::setCancelled,
                source
        ));
    }

    private ItemSourceRef itemsAdderSource(String namespacedId) {
        return namespacedId == null || namespacedId.isBlank() ? null : ItemSourceRef.orNull(ItemSourceKind.ITEMSADDER, namespacedId);
    }
}
