package emaki.jiuwu.craft.cooking;

import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import com.nexomc.nexo.api.events.custom_block.NexoBlockBreakEvent;
import com.nexomc.nexo.api.events.custom_block.NexoBlockInteractEvent;
import com.nexomc.nexo.mechanics.Mechanic;
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
 * Nexo custom-block events. Furniture events are handled by CoreLib's furniture relay; see
 * {@code CookingFurnitureEventSubscriber}.
 */
final class NexoCookingStationListener implements Listener {

    private final CookingStationListener stationListener;

    NexoCookingStationListener(CookingStationListener stationListener) {
        this.stationListener = stationListener;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNexoInteract(NexoBlockInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player == null || block == null) {
            return;
        }
        Action action = event.getAction();
        ItemSourceRef source = nexoSource(event.getMechanic());
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
    public void onNexoBreak(NexoBlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player == null || block == null) {
            return;
        }
        ItemSourceRef source = nexoSource(event.getMechanic());
        stationListener.dispatchBreak(new StationBreakContext(
                player,
                block,
                event::setCancelled,
                source
        ));
    }

    private ItemSourceRef nexoSource(Mechanic mechanic) {
        if (mechanic == null || mechanic.getItemID() == null || mechanic.getItemID().isBlank()) {
            return null;
        }
        return ItemSourceRef.orNull(ItemSourceKind.NEXO, mechanic.getItemID());
    }
}
