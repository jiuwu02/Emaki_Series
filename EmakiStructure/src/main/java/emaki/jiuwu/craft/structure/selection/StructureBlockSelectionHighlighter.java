package emaki.jiuwu.craft.structure.selection;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Structure;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.StructureBlock;
import org.bukkit.block.structure.UsageMode;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StructureBlockSelectionHighlighter implements SelectionHighlighter {

    private record AnchorRef(String world, int x, int y, int z) {
    }

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final Map<UUID, AnchorRef> anchors = new HashMap<>();
    private DisplaySelectionHighlighter fallback;
    private boolean warned;

    public StructureBlockSelectionHighlighter(EmakiStructurePlugin plugin,
            ExecutionDispatcher dispatcher) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
    }

    @Override
    public void refresh(Player player, SelectionSession session) {
        removeFrame(player, false);
        if (!session.hasBothCorners()) {
            debug(player, "refresh", "bothCorners=false world=" + session.worldName()
                    + " box=absent frame=skipped");
            return;
        }
        SelectionBox box = session.box().orElse(null);
        World world = plugin.getServer().getWorld(session.worldName());
        if (box == null || world == null) {
            debug(player, "refresh", "bothCorners=true but "
                    + (box == null ? "box=absent" : "world=" + session.worldName() + " missing")
                    + " frame=skipped");
            return;
        }
        try {
            var token = dispatcher.runAtLocation(plugin,
                    new Location(world, box.minX(), box.minY(), box.minZ()),
                    () -> sendWireframe(player, world, box));
            if (token == null) {
                debug(player, "error", "type=schedule_failed box=" + box.minX() + ","
                        + box.minY() + "," + box.minZ());
            }
        } catch (RuntimeException exception) {
            debug(player, "error", "type=" + exception.getClass().getSimpleName()
                    + " message=" + exception.getMessage());
            fallback().refresh(player, session);
        }
    }

    @Override
    public void destroy(Player player) {
        removeFrame(player, true);
    }

    private void sendWireframe(Player player, World world, SelectionBox box) {
        try {
            Location anchor = new Location(world, box.minX() - 1, box.minY() - 1,
                    box.minZ() - 1);
            BlockData data = Material.STRUCTURE_BLOCK.createBlockData();
            ((StructureBlock) data).setMode(StructureBlock.Mode.SAVE);
            player.sendBlockChange(anchor, data);
            var state = data.createBlockState();
            if (state instanceof Structure structure) {
                structure.setUsageMode(UsageMode.SAVE);
                structure.setStructureSize(new BlockVector(box.sizeX(), box.sizeY(),
                        box.sizeZ()));
                structure.setShowAir(false);
                structure.setBoundingBoxVisible(true);
                player.sendBlockUpdate(anchor, structure);
            }
            anchors.put(player.getUniqueId(),
                    new AnchorRef(world.getName(), anchor.getBlockX(), anchor.getBlockY(),
                            anchor.getBlockZ()));
            debug(player, "first_render", "mode=SAVE anchor=" + anchor.getBlockX() + ","
                    + anchor.getBlockY() + "," + anchor.getBlockZ()
                    + " size=" + box.sizeX() + "x" + box.sizeY() + "x" + box.sizeZ()
                    + " box=" + box.minX() + "," + box.minY() + "," + box.minZ()
                    + "->" + box.maxX() + "," + box.maxY() + "," + box.maxZ()
                    + " player=" + player.getLocation().getBlockX() + ","
                    + player.getLocation().getBlockY() + ","
                    + player.getLocation().getBlockZ());
        } catch (RuntimeException exception) {
            debug(player, "error", "type=" + exception.getClass().getSimpleName()
                    + " message=" + exception.getMessage());
        }
    }

    private void removeFrame(Player player, boolean log) {
        AnchorRef anchor = anchors.remove(player.getUniqueId());
        if (anchor == null) {
            return;
        }
        World world = plugin.getServer().getWorld(anchor.world());
        if (world == null) {
            return;
        }
        try {
            dispatcher.runAtLocation(plugin,
                    new Location(world, anchor.x(), anchor.y(), anchor.z()),
                    () -> player.sendBlockChange(
                            new Location(world, anchor.x(), anchor.y(), anchor.z()),
                            world.getBlockAt(anchor.x(), anchor.y(), anchor.z()).getBlockData()));
        } catch (RuntimeException exception) {
            if (log) {
                debug(player, "error", "type=restore_failed message=" + exception.getMessage());
            }
        }
        if (log) {
            debug(player, "destroy", "reason=cleared anchor=" + anchor.x() + "," + anchor.y()
                    + "," + anchor.z());
        }
    }

    private DisplaySelectionHighlighter fallback() {
        if (fallback == null) {
            fallback = new DisplaySelectionHighlighter(plugin, dispatcher);
            if (!warned) {
                warned = true;
                plugin.messageService().warning("console.selection_wireframe_fallback");
            }
        }
        return fallback;
    }

    private void debug(Player player, String step, String detail) {
        var logger = plugin.debugLogger();
        if (logger == null || !logger.shouldLog("structure", player.getUniqueId())) {
            return;
        }
        plugin.getLogger().info("[DEBUG:structure:highlight:" + step + "] player="
                + player.getName() + " " + detail);
    }
}
