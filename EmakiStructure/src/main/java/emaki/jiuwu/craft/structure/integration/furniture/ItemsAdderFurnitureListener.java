package emaki.jiuwu.craft.structure.integration.furniture;

import dev.lone.itemsadder.api.Events.ComplexFurnitureBreakEvent;
import dev.lone.itemsadder.api.Events.ComplexFurnitureInteractEvent;
import dev.lone.itemsadder.api.Events.ComplexFurniturePlacedEvent;
import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import dev.lone.itemsadder.api.Events.FurnitureInteractEvent;
import dev.lone.itemsadder.api.Events.FurniturePlacedEvent;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.multiblock.MultiblockService;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;

import java.util.UUID;

public final class ItemsAdderFurnitureListener implements Listener {

    private final EmakiStructurePlugin plugin;
    private final MultiblockService service;

    public ItemsAdderFurnitureListener(EmakiStructurePlugin plugin, MultiblockService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        try {
            if (event.getAction() == Action.LEFT_CLICK_AIR
                    || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                return;
            }
            Entity entity = event.getBukkitEntity();
            if (entity == null || event.getPlayer() == null) {
                return;
            }
            Block anchor = entity.getLocation().getBlock();
            if (anchor == null) {
                return;
            }
            service.onFurnitureInteract(event.getPlayer(), anchor, event.getNamespacedID());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("ItemsAdder 家具交互监听异常: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(FurniturePlacedEvent event) {
        try {
            Entity entity = event.getBukkitEntity();
            if (entity == null) {
                return;
            }
            Block block = entity.getLocation().getBlock();
            UUID placer = event.getPlayer() == null ? null : event.getPlayer().getUniqueId();
            service.onBlockPlaced(block.getWorld(), block.getX(), block.getY(), block.getZ(), placer);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("ItemsAdder 家具放置监听异常: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(FurnitureBreakEvent event) {
        try {
            Entity entity = event.getBukkitEntity();
            if (entity == null) {
                return;
            }
            Block block = entity.getLocation().getBlock();
            service.onMemberChanged(block.getWorld(), block.getX(), block.getY(), block.getZ());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("ItemsAdder 家具破坏监听异常: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onComplexInteract(ComplexFurnitureInteractEvent event) {
        try {
            if (event.getAction() == Action.LEFT_CLICK_AIR
                    || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                return;
            }
            if (event.getPlayer() == null) {
                return;
            }
            var furniture = event.getFurniture();
            if (furniture == null || furniture.getEntity() == null) {
                return;
            }
            Block anchor = furniture.getEntity().getLocation().getBlock();
            service.onFurnitureInteract(event.getPlayer(), anchor, event.getNamespacedID());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("ItemsAdder 复合家具交互监听异常: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onComplexPlace(ComplexFurniturePlacedEvent event) {
        try {
            var furniture = event.getFurniture();
            if (furniture == null || furniture.getEntity() == null) {
                return;
            }
            Block block = furniture.getEntity().getLocation().getBlock();
            UUID placer = event.getPlayer() == null ? null : event.getPlayer().getUniqueId();
            service.onBlockPlaced(block.getWorld(), block.getX(), block.getY(), block.getZ(), placer);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("ItemsAdder 复合家具放置监听异常: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onComplexBreak(ComplexFurnitureBreakEvent event) {
        try {
            var furniture = event.getFurniture();
            if (furniture == null || furniture.getEntity() == null) {
                return;
            }
            Block block = furniture.getEntity().getLocation().getBlock();
            service.onMemberChanged(block.getWorld(), block.getX(), block.getY(), block.getZ());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("ItemsAdder 复合家具破坏监听异常: " + exception.getMessage());
        }
    }
}
