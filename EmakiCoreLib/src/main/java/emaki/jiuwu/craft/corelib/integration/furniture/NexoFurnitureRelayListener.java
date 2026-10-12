package emaki.jiuwu.craft.corelib.integration.furniture;

import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEvent;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEventType;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

final class NexoFurnitureRelayListener implements Listener {

    private static final String NAMESPACE = NexoFurnitureBridgeApi.NAMESPACE;

    private final FurnitureEventRelay relay;

    NexoFurnitureRelayListener(FurnitureEventRelay relay) {
        this.relay = relay;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(NexoFurnitureInteractEvent event) {
        Block anchor = anchorOrLocation(event.getBaseEntity(), event.getInteractionPoint());
        if (anchor == null) {
            return;
        }
        String rawId = event.getMechanic() == null ? null : event.getMechanic().getItemID();
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.INTERACT,
                ItemSourceKind.NEXO,
                rawId,
                FurnitureEventRelay.normalizedId(NAMESPACE, rawId),
                event.getPlayer(),
                null,
                anchor,
                event.getHand() == EquipmentSlot.HAND,
                false,
                true,
                event::setCancelled), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(NexoFurniturePlaceEvent event) {
        Block anchor = anchorOrBlock(event.getBaseEntity(), event.getBlock());
        if (anchor == null) {
            return;
        }
        String rawId = event.getMechanic() == null ? null : event.getMechanic().getItemID();
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.PLACE,
                ItemSourceKind.NEXO,
                rawId,
                FurnitureEventRelay.normalizedId(NAMESPACE, rawId),
                event.getPlayer(),
                event.getPlayer() == null ? null : event.getPlayer().getUniqueId(),
                anchor,
                event.getHand() == EquipmentSlot.HAND,
                false,
                false,
                event::setCancelled), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(NexoFurnitureBreakEvent event) {
        Block anchor = anchorOnly(event.getBaseEntity());
        if (anchor == null) {
            return;
        }
        String rawId = event.getMechanic() == null ? null : event.getMechanic().getItemID();
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.BREAK,
                ItemSourceKind.NEXO,
                rawId,
                FurnitureEventRelay.normalizedId(NAMESPACE, rawId),
                event.getPlayer(),
                null,
                anchor,
                true,
                false,
                false,
                event::setCancelled), event);
    }

    private static Block anchorOrLocation(ItemDisplay baseEntity, Location fallback) {
        Block anchor = anchorOnly(baseEntity);
        if (anchor != null) {
            return anchor;
        }
        return fallback == null || fallback.getWorld() == null ? null : fallback.getBlock();
    }

    private static Block anchorOrBlock(ItemDisplay baseEntity, Block fallback) {
        Block anchor = anchorOnly(baseEntity);
        return anchor != null ? anchor : fallback;
    }

    private static Block anchorOnly(ItemDisplay baseEntity) {
        return baseEntity == null || baseEntity.getWorld() == null
                ? null : baseEntity.getLocation().getBlock();
    }
}
