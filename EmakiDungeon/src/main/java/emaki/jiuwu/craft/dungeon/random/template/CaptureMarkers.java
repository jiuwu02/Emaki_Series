package emaki.jiuwu.craft.dungeon.random.template;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.dungeon.editor.EditorModuleMarkers;
import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.ModuleNode;

public final class CaptureMarkers {

    private static final Color COLOR_SELECTION = Color.YELLOW;
    private static final Color COLOR_ROOM = Color.AQUA;
    private static final Color COLOR_CORRIDOR = Color.ORANGE;
    private static final Color COLOR_DOOR = Color.fromRGB(0xFF69B4);

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final EditorModuleMarkers renderer;
    private final Function<String, TemplateStore> storeProvider;
    private volatile emaki.jiuwu.craft.corelib.debug.DebugLogger debug;

    public CaptureMarkers(Plugin plugin, ExecutionDispatcher dispatcher,
            EditorModuleMarkers renderer, Function<String, TemplateStore> storeProvider) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.renderer = renderer;
        this.storeProvider = storeProvider;
    }

    public void bindDebug(emaki.jiuwu.craft.corelib.debug.DebugLogger debug) {
        this.debug = debug;
    }

    public void refresh(CaptureService.Session session) {
        renderer.removeAll(session.markers);
        renderer.clearLabels(EditorModuleMarkers.captureLabelGroup(session.playerId));
        if (session.worldName == null || session.worldName.isBlank()) {
            debugAnchor("refresh_skip_world_blank", Map.of());
            return;
        }
        World world = Bukkit.getWorld(session.worldName);
        if (world == null) {
            debugAnchor("refresh_skip_world_unloaded", Map.of("world", session.worldName));
            return;
        }
        String dungeonId = session.worldName.startsWith(CaptureService.EDIT_WORLD_PREFIX)
                ? session.worldName.substring(CaptureService.EDIT_WORLD_PREFIX.length())
                : "";
        UUID ownerId = session.playerId;
        Location anchor = world.getSpawnLocation();
        dispatcher.runAtLocation(plugin, anchor, () -> {
            List<Entity> out = session.markers;
            StringBuilder rendered = new StringBuilder();
            int structs = 0;
            if (!dungeonId.isBlank()) {
                for (Map.Entry<String, TemplateMeta> entry : storeProvider.apply(dungeonId).loadAll().entrySet()) {
                    renderStructure(world, ownerId, entry.getKey(), entry.getValue(), out);
                    structs++;
                    if (rendered.length() > 0) {
                        rendered.append(',');
                    }
                    rendered.append(entry.getValue().structureType()).append(':').append(entry.getKey())
                            .append('@').append(entry.getValue().selection().size());
                }
            }
            debugAnchor("refresh_rendered", Map.of(
                    "world", world.getName(),
                    "structures", String.valueOf(structs),
                    "types", rendered.toString(),
                    "doors", String.valueOf(session.anchors.size()),
                    "nodes", String.valueOf(session.nodes.size())));
            renderSessionDoors(world, ownerId, session, out);
            renderSelection(world, ownerId, session, dungeonId, out);
            renderNodes(world, ownerId, session, out);
            if (!dungeonId.isBlank()) {
                for (Map.Entry<String, TemplateMeta> entry : storeProvider.apply(dungeonId).loadAll().entrySet()) {
                    renderStructureNodes(world, ownerId, entry.getKey(), entry.getValue(), out);
                }
            }
        });
    }

    private void debugAnchor(String step, Map<String, String> fields) {
        emaki.jiuwu.craft.corelib.debug.DebugLogger logger = debug;
        if (logger == null) {
            return;
        }
        StringBuilder builder = new StringBuilder("[DEBUG:capture:render] ").append(step);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            builder.append(" | ").append(entry.getKey()).append('=').append(entry.getValue());
        }
        logger.logRaw("dungeon", (UUID) null, builder.toString());
    }

    private void renderStructure(World world, UUID ownerId, String templateId, TemplateMeta meta, List<Entity> out) {
        List<Integer> sel = meta.selection();
        if (sel.size() != 6) {
            debugAnchor("render_structure_skip_selection", Map.of(
                    "id", templateId, "size", String.valueOf(sel.size())));
            return;
        }
        EditorModuleMarkers.Box box = new EditorModuleMarkers.Box(
                Math.min(sel.get(0), sel.get(3)), Math.min(sel.get(1), sel.get(4)), Math.min(sel.get(2), sel.get(5)),
                Math.max(sel.get(0), sel.get(3)), Math.max(sel.get(1), sel.get(4)), Math.max(sel.get(2), sel.get(5)));
        Color color = TemplateModel.TYPE_CORRIDOR.equals(meta.structureType()) ? COLOR_CORRIDOR : COLOR_ROOM;
        List<Entity> edges = renderer.spawnEdges(world, ownerId, box, color);
        out.addAll(edges);
        debugAnchor("render_structure", Map.of(
                "id", templateId, "type", meta.structureType(), "color", color.asRGB() + "",
                "box", box.minX() + "," + box.minY() + "," + box.minZ() + " -> "
                        + box.maxX() + "," + box.maxY() + "," + box.maxZ(),
                "edges", String.valueOf(edges.size()),
                "world", world.getName()));
        renderer.spawnTag(world, ownerId, EditorModuleMarkers.captureLabelGroup(ownerId),
                "struct:" + templateId, box,
                renderer.structureLabel(meta.structureType(), templateId, meta.tags(), meta.weight()));
        for (var anchor : meta.anchors()) {
            EditorModuleMarkers.Box doorBox = renderer.doorBox(meta, anchor);
            if (doorBox == null) {
                continue;
            }
            out.addAll(renderer.spawnEdges(world, ownerId, doorBox, COLOR_DOOR));
            renderer.spawnTag(world, ownerId, EditorModuleMarkers.captureLabelGroup(ownerId),
                    "door:" + templateId + ":" + anchor.id(), doorBox, renderer.doorLabel(anchor.id()));
        }
    }

    private void renderSessionDoors(World world, UUID ownerId, CaptureService.Session session, List<Entity> out) {
        for (var anchor : session.anchors) {
            EditorModuleMarkers.Box doorBox = renderer.doorBox(anchor);
            if (doorBox == null) {
                continue;
            }
            out.addAll(renderer.spawnEdges(world, ownerId, doorBox, COLOR_DOOR));
            renderer.spawnTag(world, ownerId, EditorModuleMarkers.captureLabelGroup(ownerId),
                    "door:session:" + anchor.id(), doorBox, renderer.doorLabel(anchor.id()));
        }
    }

    private void renderSelection(World world, UUID ownerId, CaptureService.Session session,
            String dungeonId, List<Entity> out) {
        int[] first = session.pos1;
        int[] second = session.pos2;
        if (first == null && second == null) {
            return;
        }
        int[] a = first != null ? first : second;
        int[] b = second != null ? second : first;
        EditorModuleMarkers.Box box = new EditorModuleMarkers.Box(
                Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
        out.addAll(renderer.spawnEdges(world, ownerId, box, COLOR_SELECTION));
        if (first != null && second != null) {
            String target = containing(dungeonId, first, second);
            if (target != null) {
                renderer.spawnTag(world, ownerId, EditorModuleMarkers.captureLabelGroup(ownerId), "sel", box,
                        renderer.doorSelectionLabel(target));
            } else if (!session.tags.isEmpty()) {
                renderer.spawnTag(world, ownerId, EditorModuleMarkers.captureLabelGroup(ownerId), "sel", box,
                        renderer.selectionLabel(session.structureType, session.tags));
            }
        }
    }

    private String containing(String dungeonId, int[] first, int[] second) {
        if (dungeonId == null || dungeonId.isBlank()) {
            return null;
        }
        for (Map.Entry<String, TemplateMeta> entry : storeProvider.apply(dungeonId).loadAll().entrySet()) {
            List<Integer> sel = entry.getValue().selection();
            if (sel.size() != 6) {
                continue;
            }
            if (inside(sel, first) && inside(sel, second)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean inside(List<Integer> sel, int[] point) {
        if (point == null) {
            return false;
        }
        int minX = Math.min(sel.get(0), sel.get(3));
        int minY = Math.min(sel.get(1), sel.get(4));
        int minZ = Math.min(sel.get(2), sel.get(5));
        int maxX = Math.max(sel.get(0), sel.get(3));
        int maxY = Math.max(sel.get(1), sel.get(4));
        int maxZ = Math.max(sel.get(2), sel.get(5));
        return point[0] >= minX && point[0] <= maxX
                && point[1] >= minY && point[1] <= maxY
                && point[2] >= minZ && point[2] <= maxZ;
    }

    private void renderStructureNodes(World world, UUID ownerId, String templateId,
            TemplateMeta meta, List<Entity> out) {
        for (ModuleNode node : meta.nodes()) {
            int[] pos = meta.nodeAbsolute(node);
            EditorModuleMarkers.Box box = new EditorModuleMarkers.Box(pos[0], pos[1], pos[2], pos[0], pos[1], pos[2]);
            out.addAll(renderer.spawnEdges(world, ownerId, box, Color.fromRGB(0xFFD700)));
            renderer.spawnTag(world, ownerId, EditorModuleMarkers.captureLabelGroup(ownerId),
                    "snode:" + templateId + ":" + node.nodeId(), box,
                    renderer.nodeLabel(node.moduleType(), node.nodeId()));
        }
    }

    private void renderNodes(World world, UUID ownerId, CaptureService.Session session, List<Entity> out) {
        for (ModuleNode node : session.nodes) {
            int[] pos = session.nodeWorldPos(node);
            EditorModuleMarkers.Box box = new EditorModuleMarkers.Box(
                    pos[0], pos[1], pos[2], pos[0], pos[1], pos[2]);
            out.addAll(renderer.spawnEdges(world, ownerId, box, Color.fromRGB(0xFFD700)));
            renderer.spawnTag(world, ownerId, EditorModuleMarkers.captureLabelGroup(ownerId),
                    "node:" + node.nodeId(), box, renderer.nodeLabel(node.moduleType(), node.nodeId()));
        }
    }

}
