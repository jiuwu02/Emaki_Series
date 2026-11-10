package emaki.jiuwu.craft.dungeon.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.plugin.Plugin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;

/**
 * 地牢编辑态的功能模块可视化：为每个已放置的功能模块画出点位边框，
 * 并在边框正中心放置文本展示实体标注该模块的类型（文本走语言文件）。
 */
public final class EditorModuleMarkers {

    private static final float VIEW_RANGE = 128.0f;
    private static final float LINE_THICKNESS = 0.02f;
    private static final double POINT_HALF = 0.75D;
    private static final Quaternionf ROT_Z = new Quaternionf().rotationZ((float) (Math.PI / 2.0D));
    private static final Quaternionf ROT_Y = new Quaternionf().rotationY((float) (-Math.PI / 2.0D));

    /** 模块类型 -> flow.yml 段名，顺序即渲染顺序；与 EditorModules 的 22 节单一描述表同源。 */
    private static final Map<String, String> MODULE_SECTIONS = new LinkedHashMap<>();

    static {
        for (EditorModules.ModuleSpec spec : EditorModules.all()) {
            MODULE_SECTIONS.put(spec.type(), spec.section());
        }
    }

    private enum Axis {
        X, Y, Z
    }

    private record Edge(float x1, float y1, float z1, float x2, float y2, float z2, Axis axis) {

        float length() {
            return switch (axis) {
                case X -> x2 - x1;
                case Y -> y2 - y1;
                case Z -> z2 - z1;
            };
        }
    }

    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        double centerX() {
            return (minX + maxX) / 2.0D;
        }

        double centerY() {
            return (minY + maxY) / 2.0D;
        }

        double centerZ() {
            return (minZ + maxZ) / 2.0D;
        }
    }

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final MessageService messages;

    public EditorModuleMarkers(Plugin plugin, ExecutionDispatcher dispatcher, MessageService messages) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.messages = messages;
    }

    /** 重建该会话的全部边框与文本；返回创建的实体数量。 */
    public int refresh(EditorSession session) {
        clear(session);
        World world = Bukkit.getWorld(session.editWorldName());
        if (world == null) {
            messages.warning("console.editor_markers_world_missing", Map.of(
                    "world", String.valueOf(session.editWorldName())));
            return 0;
        }
        YamlTextDocument view = session.flowDoc().view();
        List<Entity> spawned = new ArrayList<>();
        // 边框与文本必须在该世界的 region 线程上生成，否则跨区域 spawn 会被拒绝。
        Location anchor = world.getSpawnLocation();
        dispatcher.runAtLocation(plugin, anchor, () -> {
            for (Map.Entry<String, String> entry : MODULE_SECTIONS.entrySet()) {
                String type = entry.getKey();
                String section = entry.getValue();
                try {
                    renderSection(world, session, view, type, section, spawned);
                } catch (RuntimeException exception) {
                    messages.warning("console.editor_markers_render_failed", Map.of(
                            "type", String.valueOf(type),
                            "error", String.valueOf(exception)));
                }
            }
            session.markers(spawned);
            if (!spawned.isEmpty()) {
                messages.info("console.editor_markers_spawned", Map.of(
                        "world", String.valueOf(session.editWorldName()),
                        "count", String.valueOf(spawned.size())));
            }
        });
        return spawned.size();
    }

    public void clear(EditorSession session) {
        List<Entity> existing = session.markers();
        if (existing == null || existing.isEmpty()) {
            session.markers(new ArrayList<>());
            return;
        }
        for (Entity entity : existing) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        session.markers(new ArrayList<>());
    }

    private void renderSection(World world, EditorSession session, YamlTextDocument view,
            String type, String section, List<Entity> out) {
        EditorModules.ModuleSpec spec = EditorModules.of(type);
        if (spec != null && spec.coord() == EditorModules.Coord.NONE) {
            return;
        }
        Object raw = view.value(section);
        if (!(raw instanceof Map<?, ?> value)) {
            return;
        }
        if (spec != null && spec.single()) {
            renderModule(world, session, type, section, value, out);
            return;
        }
        for (Map.Entry<?, ?> moduleEntry : value.entrySet()) {
            String moduleId = String.valueOf(moduleEntry.getKey());
            if (!(moduleEntry.getValue() instanceof Map<?, ?> module)) {
                messages.info("console.editor_markers_skip_not_map", Map.of(
                        "section", String.valueOf(section),
                        "id", String.valueOf(moduleId)));
                continue;
            }
            renderModule(world, session, type, moduleId, module, out);
        }
    }

    private void renderModule(World world, EditorSession session, String type, String moduleId,
            Map<?, ?> module, List<Entity> out) {
        Box box = boxOf(module);
        if (box == null) {
            messages.info("console.editor_markers_skip_no_box", Map.of(
                    "type", String.valueOf(type),
                    "id", String.valueOf(moduleId)));
            return;
        }
        out.addAll(spawnEdges(world, session, box, colorOf(type)));
        Entity label = spawnLabel(world, session, box, type, moduleId);
        if (label != null) {
            out.add(label);
        }
    }

    private Box boxOf(Map<?, ?> module) {
        Object region = module.get("region");
        if (region instanceof Map<?, ?> map) {
            Box box = regionBox(map);
            if (box != null) {
                return box;
            }
        }
        Object location = module.get("location");
        if (location instanceof Map<?, ?> map) {
            Double x = number(map, "x");
            Double y = number(map, "y");
            Double z = number(map, "z");
            if (x == null || y == null || z == null) {
                return null;
            }
            return new Box(x - POINT_HALF, y - POINT_HALF, z - POINT_HALF,
                    x + POINT_HALF, y + POINT_HALF, z + POINT_HALF);
        }
        return null;
    }

    /** 兼容两种 region 写法：扁平键 {@code min.x} 与嵌套 {@code min: {x,y,z}}。 */
    private Box regionBox(Map<?, ?> map) {
        Double minX = number(map, "min.x");
        Double minY = number(map, "min.y");
        Double minZ = number(map, "min.z");
        Double maxX = number(map, "max.x");
        Double maxY = number(map, "max.y");
        Double maxZ = number(map, "max.z");
        if (minX == null || minY == null || minZ == null
                || maxX == null || maxY == null || maxZ == null) {
            if (!(map.get("min") instanceof Map<?, ?> min) || !(map.get("max") instanceof Map<?, ?> max)) {
                return null;
            }
            minX = number(min, "x");
            minY = number(min, "y");
            minZ = number(min, "z");
            maxX = number(max, "x");
            maxY = number(max, "y");
            maxZ = number(max, "z");
            if (minX == null || minY == null || minZ == null
                    || maxX == null || maxY == null || maxZ == null) {
                return null;
            }
        }
        return new Box(Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ),
                Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ));
    }

    private Double number(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<Entity> spawnEdges(World world, EditorSession session, Box box, Color color) {
        List<Entity> spawned = new ArrayList<>(12);
        for (Edge edge : edges(box)) {
            Location corner = new Location(world, edge.x1(), edge.y1(), edge.z1());
            BlockDisplay line = world.spawn(corner, BlockDisplay.class, display -> {
                display.setBlock(Material.LIME_CONCRETE.createBlockData());
                display.setTransformation(lineTransformation(edge));
                display.setGlowing(true);
                display.setGlowColorOverride(color);
                display.setBrightness(new Display.Brightness(15, 0));
                display.setShadowRadius(0.0f);
                display.setViewRange(VIEW_RANGE);
                display.setTeleportDuration(1);
                display.setInterpolationDuration(0);
                display.setPersistent(false);
            });
            hideFromOthers(session.playerId(), line);
            spawned.add(line);
        }
        return spawned;
    }

    private Entity spawnLabel(World world, EditorSession session, Box box, String type, String moduleId) {
        String key = "editor.marker." + type;
        String text = messages.messageOrFallback(key, type);
        Location center = new Location(world, box.centerX(),
                Math.min(box.maxY() + 0.6D, box.centerY() + 1.2D), box.centerZ());
        TextDisplay label = world.spawn(center, TextDisplay.class, display -> {
            display.text(MiniMessages.parse("<white>" + text + "</white> <gray>" + moduleId + "</gray>"));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(180, 0, 0, 0));
            display.setShadowed(true);
            display.setBrightness(new Display.Brightness(15, 0));
            display.setViewRange(VIEW_RANGE);
            display.setPersistent(false);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
        });
        hideFromOthers(session.playerId(), label);
        return label;
    }

    private void hideFromOthers(UUID ownerId, Entity entity) {
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.getUniqueId().equals(ownerId)) {
                other.hideEntity(plugin, entity);
            }
        }
    }

    private Color colorOf(String type) {
        return switch (type) {
            case "spawn" -> Color.LIME;
            case "room" -> Color.AQUA;
            case "gate" -> Color.YELLOW;
            case "option" -> Color.FUCHSIA;
            case "trigger" -> Color.ORANGE;
            case "chest" -> Color.fromRGB(0xFFD700);
            case "exit" -> Color.RED;
            case "entry", "respawn" -> Color.fromRGB(0x7FFFD4);
            case "checkpoint" -> Color.fromRGB(0x00BFFF);
            case "boss" -> Color.fromRGB(0x8B0000);
            case "block" -> Color.fromRGB(0xA0522D);
            case "hint" -> Color.fromRGB(0xFF69B4);
            case "teleport" -> Color.fromRGB(0x9400D3);
            case "boundary" -> Color.fromRGB(0x696969);
            case "effect" -> Color.fromRGB(0x32CD32);
            case "mechanism" -> Color.fromRGB(0x4682B4);
            case "room_timer" -> Color.fromRGB(0xFF8C00);
            case "item" -> Color.fromRGB(0xDAA520);
            case "fx" -> Color.fromRGB(0xE0FFFF);
            default -> Color.WHITE;
        };
    }

    private static Transformation lineTransformation(Edge edge) {
        float length = Math.max(edge.length(), 0.01f);
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
        float length = Math.max(edge.length(), 0.01f);
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

    private static Edge[] edges(Box box) {
        float x1 = (float) box.minX();
        float y1 = (float) box.minY();
        float z1 = (float) box.minZ();
        float x2 = (float) (box.maxX() + 1.0D);
        float y2 = (float) (box.maxY() + 1.0D);
        float z2 = (float) (box.maxZ() + 1.0D);
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
