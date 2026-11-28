package emaki.jiuwu.craft.dungeon.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.util.Transformation;
import org.bukkit.plugin.Plugin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.display.DisplayGeometry;
import emaki.jiuwu.craft.corelib.display.DisplayKey;
import emaki.jiuwu.craft.corelib.display.TextDisplayService;
import emaki.jiuwu.craft.corelib.display.TextDisplaySpec;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.log.LogTag;

public final class EditorModuleMarkers {

    private static final float VIEW_RANGE = 128.0f;
    private static final float LINE_THICKNESS = 0.02f;
    private static final Quaternionf ROT_Z = new Quaternionf().rotationZ((float) (Math.PI / 2.0D));
    private static final Quaternionf ROT_Y = new Quaternionf().rotationY((float) (-Math.PI / 2.0D));

    private static final String LABEL_NAMESPACE = "emakidungeon";
    private static final DisplayGeometry.TextProfile LABEL_PROFILE = new DisplayGeometry.TextProfile(
            DisplayGeometry.Vector3.ZERO, DisplayGeometry.Vector3.ONE, "center", 200, 0xB4000000, true, false);

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

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        public double centerX() {
            return (minX + maxX) / 2.0D;
        }

        public double centerY() {
            return (minY + maxY) / 2.0D;
        }

        public double centerZ() {
            return (minZ + maxZ) / 2.0D;
        }
    }

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final MessageService messages;
    private final TextDisplayService labels;
    private volatile java.util.function.Function<String, emaki.jiuwu.craft.dungeon.random.template.TemplateStore> storeProvider;

    public void bindStoreProvider(
            java.util.function.Function<String, emaki.jiuwu.craft.dungeon.random.template.TemplateStore> storeProvider) {
        this.storeProvider = storeProvider;
    }

    public EditorModuleMarkers(Plugin plugin, ExecutionDispatcher dispatcher, MessageService messages,
            TextDisplayService labels) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.messages = messages;
        this.labels = labels;
    }

    public int refresh(EditorSession session) {
        clear(session);
        World world = Bukkit.getWorld(session.editWorldName());
        if (world == null) {
            messages.warning(LogTag.EDITOR, "console.editor_markers_world_missing", Map.of(
                    "world", String.valueOf(session.editWorldName())));
            return 0;
        }
        YamlTextDocument view = session.flowDoc().view();
        List<Entity> spawned = new ArrayList<>();
        Location anchor = world.getSpawnLocation();
        dispatcher.runAtLocation(plugin, anchor, () -> {
            for (Map.Entry<String, String> entry : MODULE_SECTIONS.entrySet()) {
                String type = entry.getKey();
                String section = entry.getValue();
                try {
                    renderSection(world, session, view, type, section, spawned);
                } catch (RuntimeException exception) {
                    messages.warning(LogTag.EDITOR, "console.editor_markers_render_failed", Map.of(
                            "type", String.valueOf(type),
                            "error", String.valueOf(exception)));
                }
            }
            renderStructures(world, session, spawned);
            renderSelection(world, session, spawned);
            session.markers(spawned);
            if (!spawned.isEmpty()) {
                messages.info(LogTag.EDITOR, "console.editor_markers_spawned", Map.of(
                        "world", String.valueOf(session.editWorldName()),
                        "count", String.valueOf(spawned.size())));
            }
        });
        return spawned.size();
    }

    public void clear(EditorSession session) {
        clearLabels(editorLabelGroup(session.playerId()));
        List<Entity> existing = session.markers();
        if (existing == null || existing.isEmpty()) {
            session.markers(new ArrayList<>());
            return;
        }
        removeAll(existing);
        session.markers(new ArrayList<>());
    }

    private void renderStructures(World world, EditorSession session, List<Entity> out) {
        java.util.function.Function<String, emaki.jiuwu.craft.dungeon.random.template.TemplateStore> provider =
                storeProvider;
        if (provider == null) {
            return;
        }
        for (Map.Entry<String, emaki.jiuwu.craft.dungeon.random.model.TemplateMeta> entry
                : provider.apply(session.dungeonId()).loadAll().entrySet()) {
            emaki.jiuwu.craft.dungeon.random.model.TemplateMeta meta = entry.getValue();
            List<Integer> sel = meta.selection();
            if (sel.size() != 6) {
                continue;
            }
            Box box = new Box(Math.min(sel.get(0), sel.get(3)), Math.min(sel.get(1), sel.get(4)),
                    Math.min(sel.get(2), sel.get(5)), Math.max(sel.get(0), sel.get(3)),
                    Math.max(sel.get(1), sel.get(4)), Math.max(sel.get(2), sel.get(5)));
            Color color = "corridor".equals(meta.structureType()) ? Color.ORANGE : Color.AQUA;
            out.addAll(spawnEdges(world, session.playerId(), box, color));
            spawnTag(world, session.playerId(), editorLabelGroup(session.playerId()),
                    "struct:" + entry.getKey(), box,
                    structureLabel(meta.structureType(), entry.getKey(), meta.tags(), meta.weight()));
            for (emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor anchor : meta.anchors()) {
                Box doorBox = doorBox(meta, anchor);
                if (doorBox == null) {
                    continue;
                }
                out.addAll(spawnEdges(world, session.playerId(), doorBox, Color.fromRGB(0xFF69B4)));
                spawnTag(world, session.playerId(), editorLabelGroup(session.playerId()),
                        "door:" + entry.getKey() + ":" + anchor.id(), doorBox, doorLabel(anchor.id()));
            }
        }
    }

    private void renderSelection(World world, EditorSession session, List<Entity> out) {
        emaki.jiuwu.craft.dungeon.flow.FlowPoint first = session.pos1();
        emaki.jiuwu.craft.dungeon.flow.FlowPoint second = session.pos2();
        if (first == null && second == null) {
            return;
        }
        emaki.jiuwu.craft.dungeon.flow.FlowPoint a = first != null ? first : second;
        emaki.jiuwu.craft.dungeon.flow.FlowPoint b = second != null ? second : first;
        Box box = new Box(Math.min(Math.floor(a.x()), Math.floor(b.x())),
                Math.min(Math.floor(a.y()), Math.floor(b.y())),
                Math.min(Math.floor(a.z()), Math.floor(b.z())),
                Math.max(Math.floor(a.x()), Math.floor(b.x())),
                Math.max(Math.floor(a.y()), Math.floor(b.y())),
                Math.max(Math.floor(a.z()), Math.floor(b.z())));
        out.addAll(spawnEdges(world, session.playerId(), box, Color.YELLOW));
    }

    private String plain(String raw) {
        return raw == null ? "" : raw.replace("<", "").replace(">", "");
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
                messages.info(LogTag.EDITOR, "console.editor_markers_skip_not_map", Map.of(
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
            messages.info(LogTag.EDITOR, "console.editor_markers_skip_no_box", Map.of(
                    "type", String.valueOf(type),
                    "id", String.valueOf(moduleId)));
            return;
        }
        out.addAll(spawnEdges(world, session.playerId(), box, colorOf(type)));
        spawnLabel(world, session.playerId(), editorLabelGroup(session.playerId()),
                "mod:" + type + ":" + moduleId, box, type, moduleId);
    }

    public Box boxOf(Map<?, ?> module) {
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
            return new Box(Math.floor(x), y, Math.floor(z), Math.floor(x), y, Math.floor(z));
        }
        return null;
    }

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

    public List<Entity> spawnEdges(World world, UUID ownerId, Box box, Color color) {
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
            hideFromOthers(ownerId, line);
            spawned.add(line);
        }
        return spawned;
    }

    public static String editorLabelGroup(UUID ownerId) {
        return "editor:" + ownerId;
    }

    public static String captureLabelGroup(UUID ownerId) {
        return "capture:" + ownerId;
    }

    public void clearLabels(String group) {
        TextDisplayService service = labels;
        if (service != null && group != null && !group.isBlank()) {
            service.removeGroup(LABEL_NAMESPACE, group);
        }
    }

    public String structureLabel(String structureType, String templateId, List<String> tags, int weight) {
        String tagsText = tags == null ? "" : String.join(",", tags);
        boolean hasTags = !tagsText.isEmpty();
        String template = messages.messageOrFallback(
                hasTags ? "editor.marker.label_structure" : "editor.marker.label_structure_notags",
                hasTags ? "<white>%type%</white> <gray>%id%</gray> <gray>[%tags%]</gray>\n<gray>weight: %weight%</gray>"
                        : "<white>%type%</white> <gray>%id%</gray>\n<gray>weight: %weight%</gray>");
        return Texts.formatTemplate(template, Map.of(
                "type", plain(structureType), "id", plain(templateId),
                "tags", plain(tagsText), "weight", String.valueOf(weight)));
    }

    public String selectionLabel(String structureType, List<String> tags) {
        String tagsText = tags == null ? "" : String.join(",", tags);
        String template = messages.messageOrFallback("editor.marker.label_selection",
                "<white>%type%</white> <gray>[%tags%]</gray>");
        return Texts.formatTemplate(template, Map.of(
                "type", plain(structureType), "tags", plain(tagsText)));
    }

    public String doorLabel(String doorId) {
        String template = messages.messageOrFallback("editor.marker.label_door",
                "<white>door</white> <gray>%id%</gray>");
        return Texts.formatTemplate(template, Map.of("id", plain(doorId)));
    }

    public String doorSelectionLabel(String targetId) {
        String template = messages.messageOrFallback("editor.marker.label_door_selection",
                "<white>door</white> <gray>-> %target%</gray>");
        return Texts.formatTemplate(template, Map.of("target", plain(targetId)));
    }

    public Box doorBox(emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor anchor) {
        return doorBox(null, anchor);
    }

    /**
     * 门锚点选框。传入模板时按模板局部坐标换算回编辑世界绝对坐标; 传 null 时锚点坐标即绝对坐标。
     */
    public Box doorBox(emaki.jiuwu.craft.dungeon.random.model.TemplateMeta meta,
            emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor anchor) {
        if (anchor == null) {
            return null;
        }
        int originX = 0;
        int originY = 0;
        int originZ = 0;
        if (meta != null) {
            int[] min = meta.selectionMin();
            originX = min[0];
            originY = min[1];
            originZ = min[2];
        }
        int x1 = Integer.MAX_VALUE;
        int y1 = Integer.MAX_VALUE;
        int z1 = Integer.MAX_VALUE;
        int x2 = Integer.MIN_VALUE;
        int y2 = Integer.MIN_VALUE;
        int z2 = Integer.MIN_VALUE;
        for (int[] cell : emaki.jiuwu.craft.dungeon.random.layout.AnchorGeometry.openingCells(anchor)) {
            x1 = Math.min(x1, originX + cell[0]);
            y1 = Math.min(y1, originY + cell[1]);
            z1 = Math.min(z1, originZ + cell[2]);
            x2 = Math.max(x2, originX + cell[0]);
            y2 = Math.max(y2, originY + cell[1]);
            z2 = Math.max(z2, originZ + cell[2]);
        }
        return new Box(x1, y1, z1, x2, y2, z2);
    }

    public String nodeLabel(String moduleType, String nodeId) {
        String template = messages.messageOrFallback("editor.marker.label_node",
                "<white>%module%</white> <gray>%id%</gray>");
        return Texts.formatTemplate(template, Map.of(
                "module", plain(moduleType), "id", plain(nodeId)));
    }

    public void spawnLabel(World world, UUID ownerId, String group, String id, Box box, String type, String moduleId) {
        String typeName = messages.messageOrFallback("editor.marker." + type, type);
        String template = messages.messageOrFallback("editor.marker.label_module",
                "<white>%type%</white> <gray>%id%</gray>");
        upsertLabel(group, id, ownerId, world, box,
                Texts.formatTemplate(template, Map.of("type", typeName, "id", String.valueOf(moduleId))));
    }

    public void spawnTag(World world, UUID ownerId, String group, String id, Box box, String text) {
        upsertLabel(group, id, ownerId, world, box, text);
    }

    private void upsertLabel(String group, String id, UUID ownerId, World world, Box box, String text) {
        TextDisplayService service = labels;
        if (service == null || text == null || text.isBlank()) {
            return;
        }
        boolean point = box.minX() == box.maxX() && box.minY() == box.maxY()
                && box.minZ() == box.maxZ();
        double labelY = (point ? box.maxY() + 1.5D
                : Math.min(box.maxY() + 0.6D, box.centerY() + 1.2D)) + 1.0D;
        double labelX = box.centerX() + 0.5D;
        double labelZ = box.centerZ() + 0.5D;
        Location base = new Location(world, labelX, labelY, labelZ);
        service.upsert(new TextDisplaySpec(new DisplayKey(LABEL_NAMESPACE, group, id), text, base,
                LABEL_PROFILE, 0, Set.of(ownerId)));
    }

    public void removeAll(List<Entity> entities) {
        if (entities == null) {
            return;
        }
        List<Entity> copy = new ArrayList<>(entities);
        entities.clear();
        for (Entity entity : copy) {
            removeEntity(entity);
        }
    }

    public static void removeAllNow(List<Entity> entities) {
        if (entities == null) {
            return;
        }
        for (Entity entity : entities) {
            if (entity != null) {
                try {
                    if (entity.isValid()) {
                        entity.remove();
                    }
                } catch (Throwable ignored) {
                    FailureRecorder.recordIgnoredFailure("EmakiDungeon", "EditorModuleMarkers.java#0", ignored);
                }
            }
        }
        entities.clear();
    }

    private void removeEntity(Entity entity) {
        if (entity == null) {
            return;
        }
        try {
            if (Bukkit.isPrimaryThread()) {
                if (entity.isValid()) {
                    entity.remove();
                }
                return;
            }
            dispatcher.runEntity(plugin, entity, () -> {
                try {
                    if (entity.isValid()) {
                        entity.remove();
                    }
                } catch (Throwable ignored) {
                    FailureRecorder.recordIgnoredFailure("EmakiDungeon", "EditorModuleMarkers.java#1", ignored);
                }
            });
        } catch (Throwable ignored) {
            FailureRecorder.recordIgnoredFailure("EmakiDungeon", "EditorModuleMarkers.java#2", ignored);
        }
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
            case "mob_spawn" -> Color.LIME;
            case "room" -> Color.AQUA;
            case "gate" -> Color.YELLOW;
            case "option" -> Color.FUCHSIA;
            case "trigger" -> Color.ORANGE;
            case "chest" -> Color.fromRGB(0xFFD700);
            case "exit" -> Color.RED;
            case "entry", "respawn" -> Color.fromRGB(0x7FFFD4);
            case "checkpoint" -> Color.fromRGB(0x00BFFF);
            case "block" -> Color.fromRGB(0xA0522D);
            case "tip" -> Color.fromRGB(0xFF69B4);
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
