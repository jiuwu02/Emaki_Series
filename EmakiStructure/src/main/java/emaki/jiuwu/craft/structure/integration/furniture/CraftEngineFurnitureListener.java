package emaki.jiuwu.craft.structure.integration.furniture;

import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.multiblock.MultiblockService;
import net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent;
import net.momirealms.craftengine.bukkit.api.event.FurnitureHitEvent;
import net.momirealms.craftengine.bukkit.api.event.FurnitureInteractEvent;
import net.momirealms.craftengine.bukkit.api.event.FurniturePlaceEvent;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

public final class CraftEngineFurnitureListener implements Listener {

    private final EmakiStructurePlugin plugin;
    private final MultiblockService service;

    public CraftEngineFurnitureListener(EmakiStructurePlugin plugin, MultiblockService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(FurnitureInteractEvent event) {
        try {
            if (event.hand() == InteractionHand.OFF_HAND) {
                return;
            }
            Block anchor = resolveAnchor(event.location());
            if (anchor == null || event.player() == null || event.furniture() == null) {
                return;
            }
            String furnitureId = event.furniture().id().toString();
            service.onFurnitureInteract(event.player(), anchor, furnitureId);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("CraftEngine 家具交互监听异常: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(FurniturePlaceEvent event) {
        try {
            Block anchor = resolveAnchor(event.location());
            if (anchor == null) {
                return;
            }
            UUID placer = event.player() == null ? null : event.player().getUniqueId();
            service.onBlockPlaced(anchor.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ(), placer);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("CraftEngine 家具放置监听异常: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(FurnitureBreakEvent event) {
        try {
            Block anchor = resolveAnchor(event.location());
            if (anchor == null) {
                return;
            }
            service.onMemberChanged(anchor.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("CraftEngine 家具破坏监听异常: " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(FurnitureHitEvent event) {
        try {
            Block anchor = resolveAnchor(event.location());
            if (anchor == null) {
                return;
            }
            service.onMemberChanged(anchor.getWorld(), anchor.getX(), anchor.getY(), anchor.getZ());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("CraftEngine 家具命中监听异常: " + exception.getMessage());
        }
    }

    private static Block resolveAnchor(Location location) {
        return location == null ? null : location.getBlock();
    }
}
