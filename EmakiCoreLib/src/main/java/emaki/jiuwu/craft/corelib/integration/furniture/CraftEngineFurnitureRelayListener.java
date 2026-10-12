package emaki.jiuwu.craft.corelib.integration.furniture;

import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEvent;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureEventType;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent;
import net.momirealms.craftengine.bukkit.api.event.FurnitureHitEvent;
import net.momirealms.craftengine.bukkit.api.event.FurnitureInteractEvent;
import net.momirealms.craftengine.bukkit.api.event.FurniturePlaceEvent;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Normalises CraftEngine furniture events (including the hit event, raised as
 * {@link FurnitureEventType#HIT} so subscribers keep their own interpretations) for the relay.
 */
final class CraftEngineFurnitureRelayListener implements Listener {

    private static final String NAMESPACE = CraftEngineFurnitureBridgeApi.NAMESPACE;

    private final FurnitureEventRelay relay;

    CraftEngineFurnitureRelayListener(FurnitureEventRelay relay) {
        this.relay = relay;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        Block anchor = resolveAnchor(event.location());
        if (anchor == null) {
            return;
        }
        String rawId = furnitureId(event.furniture());
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.INTERACT,
                ItemSourceKind.CRAFTENGINE,
                rawId,
                FurnitureEventRelay.normalizedId(NAMESPACE, rawId),
                event.player(),
                null,
                anchor,
                event.hand() == InteractionHand.MAIN_HAND,
                false,
                true,
                event::setCancelled), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(FurniturePlaceEvent event) {
        Block anchor = resolveAnchor(event.location());
        if (anchor == null) {
            return;
        }
        String rawId = furnitureId(event.furniture());
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.PLACE,
                ItemSourceKind.CRAFTENGINE,
                rawId,
                FurnitureEventRelay.normalizedId(NAMESPACE, rawId),
                event.player(),
                event.player() == null ? null : event.player().getUniqueId(),
                anchor,
                event.hand() == InteractionHand.MAIN_HAND,
                false,
                false,
                event::setCancelled), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(FurnitureBreakEvent event) {
        Block anchor = resolveAnchor(event.location());
        if (anchor == null) {
            return;
        }
        String rawId = furnitureId(event.furniture());
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.BREAK,
                ItemSourceKind.CRAFTENGINE,
                rawId,
                FurnitureEventRelay.normalizedId(NAMESPACE, rawId),
                event.player(),
                null,
                anchor,
                true,
                false,
                false,
                event::setCancelled), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(FurnitureHitEvent event) {
        Block anchor = resolveAnchor(event.location());
        if (anchor == null) {
            return;
        }
        String rawId = furnitureId(event.furniture());
        relay.dispatch(new FurnitureEvent(
                FurnitureEventType.HIT,
                ItemSourceKind.CRAFTENGINE,
                rawId,
                FurnitureEventRelay.normalizedId(NAMESPACE, rawId),
                event.player(),
                null,
                anchor,
                true,
                true,
                false,
                event::setCancelled), event);
    }

    private static String furnitureId(BukkitFurniture furniture) {
        return furniture == null || furniture.id() == null
                ? null : furniture.id().toString();
    }

    private static Block resolveAnchor(Location location) {
        return location == null || location.getWorld() == null ? null : location.getBlock();
    }
}
