package emaki.jiuwu.craft.structure.multiblock.listener;

import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.multiblock.MultiblockService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.UUID;
import emaki.jiuwu.craft.corelib.log.LogTags;
import java.util.Map;
import java.util.logging.Level;

public final class BlockChangeDetectListener implements Listener {

    private final EmakiStructurePlugin plugin;
    private final MultiblockService service;

    public BlockChangeDetectListener(EmakiStructurePlugin plugin, MultiblockService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        try {
            Block block = event.getBlockPlaced();
            UUID placer = event.getPlayer() == null ? null : event.getPlayer().getUniqueId();
            service.onBlockPlaced(block.getWorld(), block.getX(), block.getY(), block.getZ(), placer);
        } catch (RuntimeException exception) {
            LogTags.send(plugin, null, Level.WARNING, "multiblock.place_detection_error", Map.of("error", exception.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        try {
            Block block = event.getBlock();
            service.onMemberChanged(block.getWorld(), block.getX(), block.getY(), block.getZ());
        } catch (RuntimeException exception) {
            LogTags.send(plugin, null, Level.WARNING, "multiblock.break_detection_error", Map.of("error", exception.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        try {
            for (Block block : event.getBlocks()) {
                service.onMemberChanged(block.getWorld(), block.getX(), block.getY(), block.getZ());
            }
        } catch (RuntimeException exception) {
            LogTags.send(plugin, null, Level.WARNING, "multiblock.piston_detection_error", Map.of("error", exception.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        try {
            for (Block block : event.getBlocks()) {
                service.onMemberChanged(block.getWorld(), block.getX(), block.getY(), block.getZ());
            }
        } catch (RuntimeException exception) {
            LogTags.send(plugin, null, Level.WARNING, "multiblock.piston_detection_error", Map.of("error", exception.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        try {
            for (Block block : event.blockList()) {
                service.onMemberChanged(block.getWorld(), block.getX(), block.getY(), block.getZ());
            }
        } catch (RuntimeException exception) {
            LogTags.send(plugin, null, Level.WARNING, "multiblock.explosion_detection_error", Map.of("error", exception.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        try {
            for (Block block : event.blockList()) {
                service.onMemberChanged(block.getWorld(), block.getX(), block.getY(), block.getZ());
            }
        } catch (RuntimeException exception) {
            LogTags.send(plugin, null, Level.WARNING, "multiblock.explosion_detection_error", Map.of("error", exception.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        try {
            Block block = event.getBlock();
            service.onMemberChanged(block.getWorld(), block.getX(), block.getY(), block.getZ());
        } catch (RuntimeException exception) {
            LogTags.send(plugin, null, Level.WARNING, "multiblock.burn_detection_error", Map.of("error", exception.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        try {
            Block block = event.getBlock();
            service.onMemberChanged(block.getWorld(), block.getX(), block.getY(), block.getZ());
        } catch (RuntimeException exception) {
            LogTags.send(plugin, null, Level.WARNING, "multiblock.entity_change_block_detection_error", Map.of("error", exception.getMessage()));
        }
    }
}
