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

    private static final int VANILLA_SIZE_LIMIT = 48;

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
            String sizeMode = "bukkit";
            if (state instanceof Structure structure) {
                structure.setUsageMode(UsageMode.SAVE);
                boolean reflected = false;
                int sizeX = box.sizeX();
                int sizeY = box.sizeY();
                int sizeZ = box.sizeZ();
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
                            anchor.getBlockZ()));
            debug(player, "first_render", "mode=SAVE sizeMode=" + sizeMode
                    + " anchor=" + anchor.getBlockX() + ","
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

    private boolean applyLargeSize(Player player, Structure structure, int x, int y, int z) {
        try {
            java.lang.reflect.Method getHandle = structure.getClass().getMethod("getHandle");
            Object handle = getHandle.invoke(structure);
            Class<?> vec3iType = Class.forName("net.minecraft.core.Vec3i");
            Object sizeValue = newVec3i(vec3iType, x, y, z);
            java.lang.reflect.Field sizeField = findField(handle.getClass(),
                    "structureSize", "size");
            sizeField.setAccessible(true);
            sizeField.set(handle, sizeValue);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            debug(player, "reflect_fallback",
                    "type=" + exception.getClass().getSimpleName()
                            + " message=" + exception.getMessage());
            return false;
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
        if (logger == null || !logger.shouldLog("structure", player.getUniqueId())) {
            return;
        }
        plugin.getLogger().info("[DEBUG:structure:highlight:" + step + "] player="
                + player.getName() + " " + detail);
    }
}
