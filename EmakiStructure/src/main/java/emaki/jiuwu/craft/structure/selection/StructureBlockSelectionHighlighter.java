package emaki.jiuwu.craft.structure.selection;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.world.blockentity.BlockEntityTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StructureBlockSelectionHighlighter implements SelectionHighlighter {

    private static final byte MODE_SAVE = 1;

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final Map<UUID, AnchorRef> anchors = new HashMap<>();
    private DisplaySelectionHighlighter fallback;
    private boolean fallbackActive;
    private boolean warned;

    public StructureBlockSelectionHighlighter(EmakiStructurePlugin plugin,
            ExecutionDispatcher dispatcher) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
    }

    @Override
    public void refresh(Player player, SelectionSession session) {
        if (!packetEventsAvailable()) {
            fallback().refresh(player, session);
            return;
        }
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
                    new Location(world, box.minX(), box.minY(), box.minZ()), () -> {
                        if (!player.isOnline() || !session.hasBothCorners()) {
                            return;
                        }
                        SelectionBox current = session.box().orElse(null);
                        if (current == null || !session.worldName().equals(world.getName())) {
                            return;
                        }
                        sendWireframe(player, world, current);
                    });
            if (token == null) {
                debug(player, "error", "type=schedule_failed box=" + box.minX() + ","
                        + box.minY() + "," + box.minZ());
            }
        } catch (RuntimeException | LinkageError exception) {
            debug(player, "error", "type=" + exception.getClass().getSimpleName()
                    + " message=" + exception.getMessage());
            fallback().refresh(player, session);
        }
    }

    @Override
    public void destroy(Player player) {
        if (fallbackActive) {
            fallback().destroy(player);
        }
        removeFrame(player, true);
    }

    private void sendWireframe(Player player, World world, SelectionBox box) {
        Vector3i anchor = new Vector3i(box.minX(), box.minY(), box.minZ());
        NBTCompound nbt = new NBTCompound();
        nbt.setTag("x", new NBTInt(box.minX()));
        nbt.setTag("y", new NBTInt(box.minY()));
        nbt.setTag("z", new NBTInt(box.minZ()));
        nbt.setTag("name", new NBTString(""));
        nbt.setTag("mode", new NBTByte(MODE_SAVE));
        nbt.setTag("sizeX", new NBTInt(box.sizeX()));
        nbt.setTag("sizeY", new NBTInt(box.sizeY()));
        nbt.setTag("sizeZ", new NBTInt(box.sizeZ()));
        nbt.setTag("posX", new NBTInt(0));
        nbt.setTag("posY", new NBTInt(0));
        nbt.setTag("posZ", new NBTInt(0));
        nbt.setTag("showair", new NBTByte((byte) 0));
        nbt.setTag("showboundingbox", new NBTByte((byte) 1));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerBlockChange(anchor,
                        WrappedBlockState.getByString("minecraft:structure_block")));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerBlockEntityData(anchor,
                        BlockEntityTypes.STRUCTURE_BLOCK, nbt));
        anchors.put(player.getUniqueId(),
                new AnchorRef(world.getName(), box.minX(), box.minY(), box.minZ()));
        debug(player, "first_render", "mode=SAVE(" + MODE_SAVE + ")"
                + " anchor=" + box.minX() + "," + box.minY() + "," + box.minZ()
                + " size=" + box.sizeX() + "x" + box.sizeY() + "x" + box.sizeZ()
                + " box=" + box.minX() + "," + box.minY() + "," + box.minZ()
                + "->" + box.maxX() + "," + box.maxY() + "," + box.maxZ()
                + " player=" + player.getLocation().getBlockX() + ","
                + player.getLocation().getBlockY() + ","
                + player.getLocation().getBlockZ()
                + " viewDistance=" + plugin.getServer().getViewDistance());
    }

    private void removeFrame(Player player, boolean log) {
        AnchorRef anchor = anchors.remove(player.getUniqueId());
        if (anchor == null) {
            return;
        }
        World world = plugin.getServer().getWorld(anchor.world());
        if (world == null) {
            if (log) {
                debug(player, "error", "type=restore_failed message=world " + anchor.world()
                        + " missing");
            }
            return;
        }
        try {
            dispatcher.runAtLocation(plugin,
                    new Location(world, anchor.x(), anchor.y(), anchor.z()),
                    () -> restoreBlock(player, anchor));
        } catch (RuntimeException | LinkageError exception) {
            if (log) {
                debug(player, "error", "type=restore_failed message=" + exception.getMessage());
            }
        }
        if (log) {
            debug(player, "destroy", "reason=cleared anchor=" + anchor.x() + "," + anchor.y()
                    + "," + anchor.z());
        }
    }

    private void restoreBlock(Player player, AnchorRef anchor) {
        if (!player.isOnline() || !packetEventsAvailable()) {
            return;
        }
        World world = plugin.getServer().getWorld(anchor.world());
        if (world == null) {
            return;
        }
        var blockData = world.getBlockAt(anchor.x(), anchor.y(), anchor.z()).getBlockData();
        WrappedBlockState state = WrappedBlockState.getByString(blockData.getAsString());
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerBlockChange(
                        new Vector3i(anchor.x(), anchor.y(), anchor.z()), state));
    }

    private record AnchorRef(String world, int x, int y, int z) {
    }

    private DisplaySelectionHighlighter fallback() {
        if (fallback == null) {
            fallback = new DisplaySelectionHighlighter(plugin, dispatcher);
            fallbackActive = true;
        }
        return fallback;
    }

    private boolean packetEventsAvailable() {
        if (Bukkit.getPluginManager().isPluginEnabled("packetevents")
                || Bukkit.getPluginManager().isPluginEnabled("PacketEvents")) {
            return true;
        }
        if (!warned) {
            warned = true;
            plugin.messageService().warning("console.selection_wireframe_fallback");
        }
        return false;
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
