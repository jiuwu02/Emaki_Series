package emaki.jiuwu.craft.corelib.integration.furniture;

import dev.lone.itemsadder.api.Events.ComplexFurnitureBreakEvent;
import dev.lone.itemsadder.api.Events.ComplexFurnitureInteractEvent;
import dev.lone.itemsadder.api.Events.ComplexFurniturePlacedEvent;
import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import dev.lone.itemsadder.api.Events.FurnitureInteractEvent;
import dev.lone.itemsadder.api.Events.FurniturePlacedEvent;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEvent;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEventType;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;

final class ItemsAdderFurnitureRelayListener implements Listener {

    private static final String NAMESPACE = ItemsAdderFurnitureBridgeApi.NAMESPACE;

    private final FurnitureEventRelay relay;

    ItemsAdderFurnitureRelayListener(FurnitureEventRelay relay) {
        this.relay = relay;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        Block anchor = anchorBlock(event.getBukkitEntity());
        if (anchor == null) {
            return;
        }
        Action action = event.getAction();
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.INTERACT,
                ItemSourceKind.ITEMSADDER,
                event.getNamespacedID(),
                FurnitureEventRelay.normalizedId(NAMESPACE, event.getNamespacedID()),
                event.getPlayer(),
                null,
                anchor,
                true,
                isLeftClick(action),
                isRightClick(action),
                event::setCancelled), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(FurniturePlacedEvent event) {
        dispatchPlace(event.getBukkitEntity(), event.getPlayer(), event.getNamespacedID(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(FurnitureBreakEvent event) {
        Block anchor = anchorBlock(event.getBukkitEntity());
        if (anchor == null) {
            return;
        }
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.BREAK,
                ItemSourceKind.ITEMSADDER,
                event.getNamespacedID(),
                FurnitureEventRelay.normalizedId(NAMESPACE, event.getNamespacedID()),
                event.getPlayer(),
                null,
                anchor,
                true,
                false,
                false,
                event::setCancelled), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onComplexInteract(ComplexFurnitureInteractEvent event) {
        Entity entity = event.getFurniture() == null ? null : event.getFurniture().getEntity();
        Block anchor = anchorBlock(entity);
        if (anchor == null) {
            return;
        }
        Action action = event.getAction();
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.INTERACT,
                ItemSourceKind.ITEMSADDER,
                event.getNamespacedID(),
                FurnitureEventRelay.normalizedId(NAMESPACE, event.getNamespacedID()),
                event.getPlayer(),
                null,
                anchor,
                true,
                isLeftClick(action),
                isRightClick(action),
                event::setCancelled), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onComplexPlace(ComplexFurniturePlacedEvent event) {
        Entity entity = event.getFurniture() == null ? null : event.getFurniture().getEntity();
        dispatchPlace(entity, event.getPlayer(), event.getNamespacedID(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onComplexBreak(ComplexFurnitureBreakEvent event) {
        Entity entity = event.getFurniture() == null ? null : event.getFurniture().getEntity();
        Block anchor = anchorBlock(entity);
        if (anchor == null) {
            return;
        }
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.BREAK,
                ItemSourceKind.ITEMSADDER,
                event.getNamespacedID(),
                FurnitureEventRelay.normalizedId(NAMESPACE, event.getNamespacedID()),
                event.getPlayer(),
                null,
                anchor,
                true,
                false,
                false,
                event::setCancelled), event);
    }

    private void dispatchPlace(Entity entity, org.bukkit.entity.Player player, String namespacedId,
            Cancellable bukkitEvent) {
        Block anchor = anchorBlock(entity);
        if (anchor == null) {
            return;
        }
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.PLACE,
                ItemSourceKind.ITEMSADDER,
                namespacedId,
                FurnitureEventRelay.normalizedId(NAMESPACE, namespacedId),
                player,
                player == null ? null : player.getUniqueId(),
                anchor,
                true,
                false,
                false,
                bukkitEvent::setCancelled), bukkitEvent);
    }

    private static Block anchorBlock(Entity entity) {
        return entity == null || entity.getWorld() == null
                ? null : entity.getLocation().getBlock();
    }

    private static boolean isLeftClick(Action action) {
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    private static boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }
}
