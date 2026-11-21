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
    private static final double POINT_HALF = 0.75D;

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final EditorModuleMarkers renderer;
    private final Function<String, TemplateStore> storeProvider;

    public CaptureMarkers(Plugin plugin, ExecutionDispatcher dispatcher,
            EditorModuleMarkers renderer, Function<String, TemplateStore> storeProvider) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.renderer = renderer;
        this.storeProvider = storeProvider;
    }

    public void refresh(CaptureService.Session session) {
        EditorModuleMarkers.removeAll(session.markers);
        if (session.worldName == null || session.worldName.isBlank()) {
            return;
        }
        World world = Bukkit.getWorld(session.worldName);
        if (world == null) {
            return;
        }
        String dungeonId = session.worldName.startsWith(CaptureService.EDIT_WORLD_PREFIX)
                ? session.worldName.substring(CaptureService.EDIT_WORLD_PREFIX.length())
                : "";
        UUID ownerId = session.playerId;
        Location anchor = world.getSpawnLocation();
        dispatcher.runAtLocation(plugin, anchor, () -> {
            List<Entity> out = session.markers;
            if (!dungeonId.isBlank()) {
                for (Map.Entry<String, TemplateMeta> entry : storeProvider.apply(dungeonId).loadAll().entrySet()) {
                    renderStructure(world, ownerId, entry.getKey(), entry.getValue(), out);
                }
            }
            renderSelection(world, ownerId, session, out);
            renderNodes(world, ownerId, session, out);
        });
    }

    private void renderStructure(World world, UUID ownerId, String templateId, TemplateMeta meta, List<Entity> out) {
        List<Integer> sel = meta.selection();
        if (sel.size() != 6) {
            return;
        }
        EditorModuleMarkers.Box box = new EditorModuleMarkers.Box(
                Math.min(sel.get(0), sel.get(3)), Math.min(sel.get(1), sel.get(4)), Math.min(sel.get(2), sel.get(5)),
                Math.max(sel.get(0), sel.get(3)), Math.max(sel.get(1), sel.get(4)), Math.max(sel.get(2), sel.get(5)));
        Color color = TemplateModel.TYPE_CORRIDOR.equals(meta.structureType()) ? COLOR_CORRIDOR : COLOR_ROOM;
        out.addAll(renderer.spawnEdges(world, ownerId, box, color));
        Entity label = renderer.spawnTag(world, ownerId, box, tagText(meta.structureType(), templateId, meta.tags()));
        if (label != null) {
            out.add(label);
        }
    }

    private void renderSelection(World world, UUID ownerId, CaptureService.Session session, List<Entity> out) {
        int[] first = session.pos1;
        int[] second = session.pos2;
        if (first == null && second == null) {
            return;
        }
        int[] a = first != null ? first : second;
        int[] b = second != null ? second : first;
        EditorModuleMarkers.Box box = new EditorModuleMarkers.Box(
                Math.min(a[0], b[0]) - (first == null || second == null ? POINT_HALF : 0.0D),
                Math.min(a[1], b[1]) - (first == null || second == null ? POINT_HALF : 0.0D),
                Math.min(a[2], b[2]) - (first == null || second == null ? POINT_HALF : 0.0D),
                Math.max(a[0], b[0]) + (first == null || second == null ? POINT_HALF : 0.0D),
                Math.max(a[1], b[1]) + (first == null || second == null ? POINT_HALF : 0.0D),
                Math.max(a[2], b[2]) + (first == null || second == null ? POINT_HALF : 0.0D));
        out.addAll(renderer.spawnEdges(world, ownerId, box, COLOR_SELECTION));
        if (first != null && second != null && !session.tags.isEmpty()) {
            Entity label = renderer.spawnTag(world, ownerId, box,
                    tagText(session.structureType, "", session.tags));
            if (label != null) {
                out.add(label);
            }
        }
    }

    private void renderNodes(World world, UUID ownerId, CaptureService.Session session, List<Entity> out) {
        for (ModuleNode node : session.nodes) {
            int[] pos = session.nodeWorldPos(node);
            EditorModuleMarkers.Box box = new EditorModuleMarkers.Box(
                    pos[0] - POINT_HALF, pos[1] - POINT_HALF, pos[2] - POINT_HALF,
                    pos[0] + POINT_HALF, pos[1] + POINT_HALF, pos[2] + POINT_HALF);
            out.addAll(renderer.spawnEdges(world, ownerId, box, Color.fromRGB(0xFFD700)));
            Entity label = renderer.spawnTag(world, ownerId, box,
                    "<white>" + plain(node.moduleType()) + "</white> <gray>" + plain(node.nodeId()) + "</gray>");
            if (label != null) {
                out.add(label);
            }
        }
    }

    private String tagText(String structureType, String templateId, List<String> tags) {
        StringBuilder text = new StringBuilder("<white>").append(plain(structureType)).append("</white>");
        if (templateId != null && !templateId.isBlank()) {
            text.append(" <gray>").append(plain(templateId)).append("</gray>");
        }
        if (tags != null && !tags.isEmpty()) {
            text.append(" <gray>[").append(plain(String.join(",", tags))).append("]</gray>");
        }
        return text.toString();
    }

    private String plain(String raw) {
        return raw == null ? "" : raw.replace("<", "").replace(">", "");
    }
}
