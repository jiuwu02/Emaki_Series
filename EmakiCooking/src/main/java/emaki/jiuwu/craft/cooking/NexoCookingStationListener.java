package emaki.jiuwu.craft.cooking;

import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import com.nexomc.nexo.api.events.custom_block.NexoBlockBreakEvent;
import com.nexomc.nexo.api.events.custom_block.NexoBlockInteractEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent;
import com.nexomc.nexo.mechanics.Mechanic;
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
        ItemSource source = nexoSource(event.getMechanic());
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
        ItemSource source = nexoSource(event.getMechanic());
        stationListener.dispatchBreak(new StationBreakContext(
                player,
                block,
                event::setCancelled,
                source
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNexoFurnitureInteract(NexoFurnitureInteractEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getBaseEntity();
        ItemSource source = nexoSource(event.getMechanic());
        Block block = anchorBlock(entity);
        if (player == null || block == null || source == null) {
            return;
        }
        stationListener.dispatchInteraction(new StationInteraction(
                player,
                block,
                false,
                true,
                event.getHand() == EquipmentSlot.HAND,
                event::setCancelled,
                source
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNexoFurnitureBreak(NexoFurnitureBreakEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getBaseEntity();
        ItemSource source = nexoSource(event.getMechanic());
        Block block = anchorBlock(entity);
        if (block == null || source == null) {
            return;
        }
        stationListener.dispatchBreak(new StationBreakContext(
                player,
                block,
                event::setCancelled,
                source
        ));
    }

    private Block anchorBlock(Entity entity) {
        return entity == null || entity.getWorld() == null ? null : entity.getLocation().getBlock();
    }

    private ItemSource nexoSource(Mechanic mechanic) {
        if (mechanic == null || mechanic.getItemID() == null || mechanic.getItemID().isBlank()) {
            return null;
        }
        return new ItemSource(ItemSourceType.NEXO, mechanic.getItemID());
    }
}
