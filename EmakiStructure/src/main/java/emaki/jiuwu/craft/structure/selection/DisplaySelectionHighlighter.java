package emaki.jiuwu.craft.structure.selection;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DisplaySelectionHighlighter implements SelectionHighlighter {

    private static final int EDGE_COUNT = 12;
    private static final float VIEW_RANGE = 4.0f;
    private static final float LINE_THICKNESS = 0.03f;
    private static final float HALF_THICKNESS = LINE_THICKNESS / 2.0f;
    private static final Quaternionf ROT_Z = new Quaternionf().rotationZ((float) (Math.PI / 2.0d));
    private static final Quaternionf ROT_Y = new Quaternionf().rotationY((float) (-Math.PI / 2.0d));

    private enum Axis {
        X, Y, Z
    }

    private record Edge(int x1, int y1, int z1, int x2, int y2, int z2, Axis axis) {

        double length() {
            return switch (axis) {
                case X -> x2 - x1;
                case Y -> y2 - y1;
                case Z -> z2 - z1;
            };
        }
    }

    private record Frame(List<Entity> edges, String box, String world) {
    }

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final Map<UUID, Frame> frames = new HashMap<>();

    public DisplaySelectionHighlighter(EmakiStructurePlugin plugin, ExecutionDispatcher dispatcher) {
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
        var world = plugin.getServer().getWorld(session.worldName());
        if (box == null || world == null) {
            debug(player, "refresh", "bothCorners=true but "
                    + (box == null ? "box=absent" : "world=" + session.worldName() + " missing")
                    + " frame=skipped");
            return;
        }
        Location anchor = new Location(world, box.minX(), box.minY(), box.minZ());
        boolean scheduled = schedule(player, session, world, box, anchor);
        debug(player, "refresh", "bothCorners=true world=" + session.worldName()
                + " box=" + box.minX() + "," + box.minY() + "," + box.minZ()
                + "->" + box.maxX() + "," + box.maxY() + "," + box.maxZ()
                + " frame=" + (scheduled ? "scheduled" : "schedule_failed"));
    }

    @Override
    public void destroy(Player player) {
        removeFrame(player, true);
    }

    private void removeFrame(Player player, boolean log) {
        Frame frame = frames.remove(player.getUniqueId());
        if (frame == null) {
            return;
        }
        for (Entity edge : frame.edges()) {
            edge.remove();
        }
        if (log) {
            debug(player, "destroy", "reason=cleared edges=" + frame.edges().size()
                    + " box=" + frame.box() + " world=" + frame.world());
        }
    }

    private boolean schedule(Player player, SelectionSession session, org.bukkit.World world,
            SelectionBox box, Location anchor) {
        try {
            var token = dispatcher.runAtLocation(plugin, anchor, () -> {
                if (!player.isOnline() || !session.hasBothCorners()) {
                    return;
                }
                SelectionBox current = session.box().orElse(null);
                if (current == null || !session.worldName().equals(world.getName())) {
                    return;
                }
                List<Entity> edges = new ArrayList<>(EDGE_COUNT);
                for (Edge edge : edges(current)) {
                    Location corner = new Location(world, edge.x1(), edge.y1(), edge.z1());
                    BlockDisplay line = world.spawn(corner, BlockDisplay.class, spawned -> {
                        spawned.setBlock(Material.LIME_CONCRETE.createBlockData());
                        spawned.setTransformation(lineTransformation(edge));
                        spawned.setBrightness(new Display.Brightness(15, 15));
                        spawned.setShadowRadius(0.0f);
                        spawned.setViewRange(VIEW_RANGE);
                        spawned.setTeleportDuration(1);
                        spawned.setInterpolationDuration(0);
                    });
                    hideFromOthers(player, line);
                    edges.add(line);
                }
                frames.put(player.getUniqueId(), new Frame(edges,
                        current.minX() + "," + current.minY() + "," + current.minZ()
                                + "->" + current.maxX() + "," + current.maxY() + "," + current.maxZ(),
                        world.getName()));
                debug(player, "first_render", "edges=" + edges.size()
                        + " box=" + current.minX() + "," + current.minY() + "," + current.minZ()
                        + "->" + current.maxX() + "," + current.maxY() + "," + current.maxZ()
                        + " player=" + player.getLocation().getBlockX() + ","
                        + player.getLocation().getBlockY() + ","
                        + player.getLocation().getBlockZ()
                        + " viewDistance=" + plugin.getServer().getViewDistance());
            });
            return token != null;
        } catch (RuntimeException exception) {
            debug(player, "error", "type=" + exception.getClass().getSimpleName()
                    + " message=" + exception.getMessage());
            plugin.getLogger().warning("选区线框创建失败: " + exception.getMessage());
            return false;
        }
    }

    private void hideFromOthers(Player target, Entity line) {
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(target)) {
                other.hideEntity(plugin, line);
            }
        }
    }

    private void debug(Player player, String step, String detail) {
        var logger = plugin.debugLogger();
        if (logger == null || !logger.shouldLog("structure", player.getUniqueId())) {
            return;
        }
        plugin.getLogger().info("[DEBUG:structure:highlight:" + step + "] player="
                + player.getName() + " " + detail);
    }

    private static Transformation lineTransformation(Edge edge) {
        Vector3f scale = new Vector3f((float) edge.length(), LINE_THICKNESS, LINE_THICKNESS);
        Vector3f offset = switch (edge.axis()) {
            case X -> new Vector3f(0.0f, -HALF_THICKNESS, -HALF_THICKNESS);
            case Y -> new Vector3f(-HALF_THICKNESS, 0.0f, -HALF_THICKNESS);
            case Z -> new Vector3f(-HALF_THICKNESS, -HALF_THICKNESS, 0.0f);
        };
        Quaternionf rotation = switch (edge.axis()) {
            case X -> new Quaternionf();
            case Y -> ROT_Z;
            case Z -> ROT_Y;
        };
        return new Transformation(offset, rotation, scale, new Quaternionf());
    }

    private static Edge[] edges(SelectionBox box) {
        int x1 = box.minX();
        int y1 = box.minY();
        int z1 = box.minZ();
        int x2 = box.maxX() + 1;
        int y2 = box.maxY() + 1;
        int z2 = box.maxZ() + 1;
        return new Edge[] {
                new Edge(x1, y1, z1, x2, y1, z1, Axis.X),
                new Edge(x1, y1, z2, x2, y1, z2, Axis.X),
                new Edge(x1, y2, z1, x2, y2, z1, Axis.X),
                new Edge(x1, y2, z2, x2, y2, z2, Axis.X),
                new Edge(x1, y1, z1, x1, y1, z2, Axis.Z),
                new Edge(x2, y1, z1, x2, y1, z2, Axis.Z),
                new Edge(x1, y2, z1, x1, y2, z2, Axis.Z),
                new Edge(x2, y2, z1, x2, y2, z2, Axis.Z),
                new Edge(x1, y1, z1, x1, y2, z1, Axis.Y),
                new Edge(x2, y1, z1, x2, y2, z1, Axis.Y),
                new Edge(x1, y1, z2, x1, y2, z2, Axis.Y),
                new Edge(x2, y1, z2, x2, y2, z2, Axis.Y)
        };
    }
}
