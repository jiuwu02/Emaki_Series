package emaki.jiuwu.craft.structure.integration.furniture;

import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.multiblock.MultiblockService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

public final class NexoFurnitureListener implements Listener {

    private final EmakiStructurePlugin plugin;
    private final MultiblockService service;

    public NexoFurnitureListener(EmakiStructurePlugin plugin, MultiblockService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(NexoFurnitureInteractEvent event) {
        try {
            if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            Block anchor = resolveAnchor(event.getInteractionPoint(), event.getBaseEntity());
            if (anchor == null || event.getPlayer() == null) {
                return;
            }
            service.onFurnitureInteract(event.getPlayer(), anchor,
                    event.getMechanic() == null ? null : event.getMechanic().getItemID());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Nexo 家具交互监听异常: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(NexoFurniturePlaceEvent event) {
        try {
            Block anchor = resolveAnchor(null, event.getBaseEntity());
            if (anchor == null) {
                return;
            }
            UUID placer = event.getPlayer() == null ? null : event.getPlayer().getUniqueId();
            service.onBlockPlaced(anchor.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ(), placer);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Nexo 家具放置监听异常: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(NexoFurnitureBreakEvent event) {
        try {
            Block anchor = resolveAnchor(null, event.getBaseEntity());
            if (anchor == null) {
                return;
            }
            service.onMemberChanged(anchor.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Nexo 家具破坏监听异常: " + exception.getMessage());
        }
    }

    private static Block resolveAnchor(Location interactionPoint, ItemDisplay baseEntity) {
        if (interactionPoint != null && interactionPoint.getBlock() != null) {
            return interactionPoint.getBlock();
        }
        if (baseEntity == null || baseEntity.getLocation() == null) {
            return null;
        }
        return baseEntity.getLocation().getBlock();
    }
}
