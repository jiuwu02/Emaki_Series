package emaki.jiuwu.craft.structure.integration.furniture;

import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.multiblock.MultiblockService;
import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureBreakEvent;
import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureInteractEvent;
import io.th0rgal.oraxen.api.events.furniture.OraxenFurniturePlaceEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

public final class OraxenFurnitureListener implements Listener {

    private final EmakiStructurePlugin plugin;
    private final MultiblockService service;

    public OraxenFurnitureListener(EmakiStructurePlugin plugin, MultiblockService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(OraxenFurnitureInteractEvent event) {
        try {
            if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            Block anchor = resolveAnchor(event.getBlock(), event.getBaseEntity());
            if (anchor == null || event.getPlayer() == null) {
                return;
            }
            service.onFurnitureInteract(event.getPlayer(), anchor,
                    event.getMechanic() == null ? null : event.getMechanic().getItemID());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Oraxen 家具交互监听异常: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(OraxenFurniturePlaceEvent event) {
        try {
            Block anchor = resolveAnchor(event.getBlock(), event.getBaseEntity());
            if (anchor == null) {
                return;
            }
            UUID placer = event.getPlayer() == null ? null : event.getPlayer().getUniqueId();
            service.onBlockPlaced(anchor.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ(), placer);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Oraxen 家具放置监听异常: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(OraxenFurnitureBreakEvent event) {
        try {
            Block anchor = event.getBlock() != null
                    ? event.getBlock()
                    : resolveAnchor(null, event.getBaseEntity());
            if (anchor == null) {
                return;
            }
            service.onMemberChanged(anchor.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Oraxen 家具破坏监听异常: " + exception.getMessage());
        }
    }

    private static Block resolveAnchor(Block block, Entity baseEntity) {
        if (block != null) {
            return block;
        }
        if (baseEntity == null || baseEntity.getLocation() == null) {
            return null;
        }
        return baseEntity.getLocation().getBlock();
    }
}
