package emaki.jiuwu.craft.structure.selection;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import org.bukkit.Color;
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
    private static final float LINE_THICKNESS = 0.015f;
    private static final Quaternionf ROT_Z = new Quaternionf().rotationZ((float) (Math.PI / 2.0d));
    private static final Quaternionf ROT_Y = new Quaternionf().rotationY((float) (-Math.PI / 2.0d));

    private enum Axis {
        X, Y, Z
    }

    private record Edge(int x1, int y1, int z1, int x2, int y2, int z2, Axis axis, 
            boolean pos1Axis) {

        double length() {
            return switch (axis) {
                case X -> x2 - x1;
                case Y -> y2 - y1;
                case Z -> z2 - z1;
            };
        }

        Color glowColor() {
            if (!pos1Axis) {
                return Color.WHITE;
            }
            return switch (axis) {
                case X -> Color.fromRGB(255, 0, 0);
                case Y -> Color.fromRGB(0, 255, 0);
                case Z -> Color.fromRGB(0, 0, 255);
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
                        spawned.setGlowing(true);
                        spawned.setGlowColorOverride(edge.glowColor());
                        spawned.setBrightness(new Display.Brightness(15, 0));
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
        float length = (float) edge.length();
        Vector3f scale = new Vector3f(length, LINE_THICKNESS, LINE_THICKNESS);
        Quaternionf rotation = switch (edge.axis()) {
            case X -> new Quaternionf();
            case Y -> ROT_Z;
            case Z -> ROT_Y;
        };
        Vector3f rotatedCenter = rotateVector(rotation,
                new Vector3f(scale.x() / 2.0f, scale.y() / 2.0f, scale.z() / 2.0f));
        Vector3f offset = targetCenter(edge).sub(rotatedCenter);
        return new Transformation(offset, rotation, scale, new Quaternionf());
    }

    private static Vector3f targetCenter(Edge edge) {
        float length = (float) edge.length();
        return switch (edge.axis()) {
            case X -> new Vector3f(length / 2.0f, 0.0f, 0.0f);
            case Y -> new Vector3f(0.0f, length / 2.0f, 0.0f);
            case Z -> new Vector3f(0.0f, 0.0f, length / 2.0f);
        };
    }

    private static Vector3f rotateVector(Quaternionf q, Vector3f v) {
        float tx = 2.0f * (q.y() * v.z - q.z() * v.y);
        float ty = 2.0f * (q.z() * v.x - q.x() * v.z);
        float tz = 2.0f * (q.x() * v.y - q.y() * v.x);
        return new Vector3f(
                v.x + q.w() * tx + (q.y() * tz - q.z() * ty),
                v.y + q.w() * ty + (q.z() * tx - q.x() * tz),
                v.z + q.w() * tz + (q.x() * ty - q.y() * tx));
    }

    private static Edge[] edges(SelectionBox box) {
        int x1 = box.minX();
        int y1 = box.minY();
        int z1 = box.minZ();
        int x2 = box.maxX() + 1;
        int y2 = box.maxY() + 1;
        int z2 = box.maxZ() + 1;
        return new Edge[] {
                new Edge(x1, y1, z1, x2, y1, z1, Axis.X, true),
                new Edge(x1, y1, z2, x2, y1, z2, Axis.X, false),
                new Edge(x1, y2, z1, x2, y2, z1, Axis.X, false),
                new Edge(x1, y2, z2, x2, y2, z2, Axis.X, false),
                new Edge(x1, y1, z1, x1, y1, z2, Axis.Z, true),
                new Edge(x2, y1, z1, x2, y1, z2, Axis.Z, false),
                new Edge(x1, y2, z1, x1, y2, z2, Axis.Z, false),
                new Edge(x2, y2, z1, x2, y2, z2, Axis.Z, false),
                new Edge(x1, y1, z1, x1, y2, z1, Axis.Y, true),
                new Edge(x2, y1, z1, x2, y2, z1, Axis.Y, false),
                new Edge(x1, y1, z2, x1, y2, z2, Axis.Y, false),
                new Edge(x2, y1, z2, x2, y2, z2, Axis.Y, false)
        };
    }
}
