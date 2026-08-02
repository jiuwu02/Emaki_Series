package emaki.jiuwu.craft.cooking;

import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureBreakEvent;
import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureInteractEvent;
import io.th0rgal.oraxen.api.events.noteblock.OraxenNoteBlockBreakEvent;
import io.th0rgal.oraxen.api.events.noteblock.OraxenNoteBlockInteractEvent;
import io.th0rgal.oraxen.api.events.stringblock.OraxenStringBlockBreakEvent;
import io.th0rgal.oraxen.api.events.stringblock.OraxenStringBlockInteractEvent;
import io.th0rgal.oraxen.mechanics.Mechanic;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

final class OraxenCookingStationListener implements Listener {

    private final CookingStationListener stationListener;

    OraxenCookingStationListener(CookingStationListener stationListener) {
        this.stationListener = stationListener;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOraxenNoteBlockInteract(OraxenNoteBlockInteractEvent event) {
        dispatchBlockInteraction(
                event.getPlayer(),
                event.getBlock(),
                event.getAction(),
                event.getHand(),
                event::setCancelled,
                oraxenSource(event.getMechanic())
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOraxenNoteBlockBreak(OraxenNoteBlockBreakEvent event) {
        dispatchBlockBreak(event.getPlayer(), event.getBlock(), event::setCancelled, oraxenSource(event.getMechanic()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOraxenStringBlockInteract(OraxenStringBlockInteractEvent event) {
        dispatchBlockInteraction(
                event.getPlayer(),
                event.getBlock(),
                Action.RIGHT_CLICK_BLOCK,
                event.getHand(),
                event::setCancelled,
                oraxenSource(event.getMechanic())
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOraxenStringBlockBreak(OraxenStringBlockBreakEvent event) {
        dispatchBlockBreak(event.getPlayer(), event.getBlock(), event::setCancelled, oraxenSource(event.getMechanic()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOraxenFurnitureInteract(OraxenFurnitureInteractEvent event) {
        Block block = furnitureBlock(event.getBlock(), event.getBaseEntity());
        ItemSourceRef source = oraxenSource(event.getMechanic());
        Player player = event.getPlayer();
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
    public void onOraxenFurnitureBreak(OraxenFurnitureBreakEvent event) {
        Block block = furnitureBlock(event.getBlock(), event.getBaseEntity());
        ItemSourceRef source = oraxenSource(event.getMechanic());
        if (block == null || source == null) {
            return;
        }
        stationListener.dispatchBreak(new StationBreakContext(
                event.getPlayer(),
                block,
                event::setCancelled,
                source
        ));
    }

    private void dispatchBlockInteraction(Player player,
            Block block,
            Action action,
            EquipmentSlot hand,
            java.util.function.Consumer<Boolean> cancelConsumer,
            ItemSourceRef source) {
        if (player == null || block == null) {
            return;
        }
        stationListener.dispatchInteraction(new StationInteraction(
                player,
                block,
                action == Action.LEFT_CLICK_BLOCK,
                action == Action.RIGHT_CLICK_BLOCK,
                hand == EquipmentSlot.HAND,
                cancelConsumer,
                source
        ));
    }

    private void dispatchBlockBreak(Player player,
            Block block,
            java.util.function.Consumer<Boolean> cancelConsumer,
            ItemSourceRef source) {
        if (player == null || block == null) {
            return;
        }
        stationListener.dispatchBreak(new StationBreakContext(
                player,
                block,
                cancelConsumer,
                source
        ));
    }

    private Block furnitureBlock(Block hitbox, Entity baseEntity) {
        if (hitbox != null) {
            return hitbox;
        }
        return baseEntity == null || baseEntity.getWorld() == null ? null : baseEntity.getLocation().getBlock();
    }

    private ItemSourceRef oraxenSource(Mechanic mechanic) {
        if (mechanic == null || mechanic.getItemID() == null || mechanic.getItemID().isBlank()) {
            return null;
        }
        return ItemSourceRef.orNull(ItemSourceKind.ORAXEN, mechanic.getItemID());
    }
}
