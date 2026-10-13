package emaki.jiuwu.craft.structure.selection;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.BlockVector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StructureBlockSelectionHighlighter
        implements SelectionHighlighter, Listener {

    private static final int VANILLA_SIZE_LIMIT = 48;
    private static final long RERENDER_PERIOD_TICKS = 20L;

    private record AnchorRef(String world, int x, int y, int z, SelectionBox box) {
    }

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final Map<UUID, AnchorRef> anchors = new HashMap<>();
    private final TaskToken rerenderTask;
    private DisplaySelectionHighlighter fallback;
    private boolean warned;

    public StructureBlockSelectionHighlighter(EmakiStructurePlugin plugin,
            ExecutionDispatcher dispatcher) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.rerenderTask = dispatcher.runGlobalTimer(plugin, this::rerenderAll,
                RERENDER_PERIOD_TICKS, RERENDER_PERIOD_TICKS);
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

    private void rerenderAll() {
        for (Map.Entry<UUID, AnchorRef> entry : anchors.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            AnchorRef anchor = entry.getValue();
            World world = plugin.getServer().getWorld(anchor.world());
            if (world == null) {
                continue;
            }
            try {
                dispatcher.runAtLocation(plugin,
                        new Location(world, anchor.x(), anchor.y(), anchor.z()),
                        () -> sendWireframe(player, world, anchor.box()));
            } catch (RuntimeException ignored) {
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        int x = event.getBlock().getX();
        int y = event.getBlock().getY();
        int z = event.getBlock().getZ();
        String worldName = event.getBlock().getWorld().getName();
        for (Map.Entry<UUID, AnchorRef> entry : anchors.entrySet()) {
            AnchorRef anchor = entry.getValue();
            if (!anchor.world().equals(worldName) || anchor.x() != x
                    || anchor.y() != y || anchor.z() != z) {
                continue;
            }
            event.setCancelled(true);
            Player owner = plugin.getServer().getPlayer(entry.getKey());
            if (owner != null) {
                World world = plugin.getServer().getWorld(anchor.world());
                if (world != null) {
                    sendWireframe(owner, world, anchor.box());
                }
            }
            debug(owner, "break_cancelled",
                    "world=" + worldName + " at=" + x + "," + y + "," + z
                            + " reRendered=" + (owner != null));
            return;
        }
    }

    private void sendWireframe(Player player, World world, SelectionBox box) {
        try {
            Location anchor = new Location(world, box.minX() - 1, box.minY() - 1,
                    box.minZ() - 1);
            BlockData data = Material.STRUCTURE_BLOCK.createBlockData();
            ((StructureBlock) data).setMode(StructureBlock.Mode.SAVE);
            player.sendBlockChange(anchor, data);
            var state = data.createBlockState();
            String sizeMode = "bukkit";
            int sizeX = box.sizeX();
            int sizeY = box.sizeY();
            int sizeZ = box.sizeZ();
            if (state instanceof Structure structure) {
                structure.setUsageMode(UsageMode.SAVE);
                boolean reflected = false;
                if (sizeX > VANILLA_SIZE_LIMIT || sizeY > VANILLA_SIZE_LIMIT
                        || sizeZ > VANILLA_SIZE_LIMIT) {
                    if (applyLargeSize(player, structure, sizeX, sizeY, sizeZ)) {
                        sizeMode = "reflect";
                        reflected = true;
                    } else {
                        structure.setStructureSize(new BlockVector(
                                Math.min(sizeX, VANILLA_SIZE_LIMIT),
                                Math.min(sizeY, VANILLA_SIZE_LIMIT),
                                Math.min(sizeZ, VANILLA_SIZE_LIMIT)));
                        sizeMode = "clamped48";
                    }
                }
                if (!reflected) {
                    if (sizeX <= VANILLA_SIZE_LIMIT && sizeY <= VANILLA_SIZE_LIMIT
                            && sizeZ <= VANILLA_SIZE_LIMIT) {
                        structure.setStructureSize(new BlockVector(sizeX, sizeY, sizeZ));
                    }
                }
                structure.setShowAir(false);
                structure.setBoundingBoxVisible(true);
                player.sendBlockUpdate(anchor, structure);
            }
            anchors.put(player.getUniqueId(),
                    new AnchorRef(world.getName(), anchor.getBlockX(), anchor.getBlockY(),
                            anchor.getBlockZ(), box));
            debug(player, "first_render", "mode=SAVE sizeMode=" + sizeMode
                    + " anchor=" + anchor.getBlockX() + ","
                    + anchor.getBlockY() + "," + anchor.getBlockZ()
                    + " size=" + sizeX + "x" + sizeY + "x" + sizeZ
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

    private boolean applyLargeSize(Player player, Structure structure, int x, int y, int z) {
        try {
            Object snapshot = resolveSnapshot(structure);
            Class<?> vec3iType = Class.forName("net.minecraft.core.Vec3i");
            Object sizeValue = newVec3i(vec3iType, x, y, z);
            java.lang.reflect.Field sizeField = findField(snapshot.getClass(),
                    "structureSize", "size");
            sizeField.setAccessible(true);
            sizeField.set(snapshot, sizeValue);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            debug(player, "reflect_fallback",
                    "snapshot=" + resolveSnapshotType(structure)
                            + " type=" + exception.getClass().getSimpleName()
                            + " message=" + exception.getMessage());
            return false;
        }
    }

    private static Object resolveSnapshot(Structure structure)
            throws ReflectiveOperationException {
        ReflectiveOperationException last = null;
        for (String name : new String[] { "getSnapshot", "getSnapshotNMS", "getHandle" }) {
            try {
                return structure.getClass().getMethod(name).invoke(structure);
            } catch (NoSuchMethodException exception) {
                last = exception;
            }
        }
        throw last != null ? last
                : new NoSuchMethodException("getSnapshot/getSnapshotNMS/getHandle");
    }

    private String resolveSnapshotType(Structure structure) {
        try {
            Object snapshot = resolveSnapshot(structure);
            return snapshot.getClass().getName();
        } catch (ReflectiveOperationException exception) {
            return "resolve_failed(" + exception.getMessage() + ")";
        }
    }

    private static Object newVec3i(Class<?> vec3iType, int x, int y, int z)
            throws ReflectiveOperationException {
        try {
            return vec3iType.getConstructor(int.class, int.class, int.class)
                    .newInstance(x, y, z);
        } catch (NoSuchMethodException ignored) {
            return vec3iType.getConstructor(double.class, double.class, double.class)
                    .newInstance((double) x, (double) y, (double) z);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> type, String... names)
            throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchFieldException(String.join("/", names));
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
        if (logger == null || !logger.shouldLog("structure",
                player == null ? null : player.getUniqueId())) {
            return;
        }
        plugin.getLogger().info("[DEBUG:structure:highlight:" + step + "] player="
                + (player == null ? "(offline)" : player.getName()) + " " + detail);
    }
}
