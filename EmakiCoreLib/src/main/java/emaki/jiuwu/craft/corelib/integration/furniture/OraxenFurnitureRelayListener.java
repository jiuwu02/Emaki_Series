package emaki.jiuwu.craft.corelib.integration.furniture;

import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEvent;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEventType;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureBreakEvent;
import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureInteractEvent;
import io.th0rgal.oraxen.api.events.furniture.OraxenFurniturePlaceEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

final class OraxenFurnitureRelayListener implements Listener {

    private static final String NAMESPACE = OraxenFurnitureBridgeApi.NAMESPACE;

    private final FurnitureEventRelay relay;

    OraxenFurnitureRelayListener(FurnitureEventRelay relay) {
        this.relay = relay;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(OraxenFurnitureInteractEvent event) {
        Block anchor = resolveAnchor(event.getBlock(), event.getBaseEntity());
        if (anchor == null) {
            return;
        }
        String rawId = event.getMechanic() == null ? null : event.getMechanic().getItemID();
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.INTERACT,
                ItemSourceKind.ORAXEN,
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
    public void onPlace(OraxenFurniturePlaceEvent event) {
        Block anchor = resolveAnchor(event.getBlock(), event.getBaseEntity());
        if (anchor == null) {
            return;
        }
        String rawId = event.getMechanic() == null ? null : event.getMechanic().getItemID();
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.PLACE,
                ItemSourceKind.ORAXEN,
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
    public void onBreak(OraxenFurnitureBreakEvent event) {
        Block anchor = resolveAnchor(event.getBlock(), event.getBaseEntity());
        if (anchor == null) {
            return;
        }
        String rawId = event.getMechanic() == null ? null : event.getMechanic().getItemID();
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.BREAK,
                ItemSourceKind.ORAXEN,
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

    private static Block resolveAnchor(Block block, Entity baseEntity) {
        if (block != null) {
            return block;
        }
        return baseEntity == null || baseEntity.getWorld() == null
                ? null : baseEntity.getLocation().getBlock();
    }
}
