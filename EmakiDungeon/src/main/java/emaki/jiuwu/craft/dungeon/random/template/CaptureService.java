package emaki.jiuwu.craft.dungeon.random.template;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import emaki.jiuwu.craft.dungeon.editor.EditorModuleMarkers;
import emaki.jiuwu.craft.dungeon.random.config.RandomConfig;
import emaki.jiuwu.craft.dungeon.random.layout.AnchorGeometry;
import emaki.jiuwu.craft.dungeon.random.model.Mask;
import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.ModuleNode;
import emaki.jiuwu.craft.dungeon.random.nbt.StructureNbt;
import emaki.jiuwu.craft.dungeon.random.nbt.StructureNbt.BlockEntry;
import emaki.jiuwu.craft.dungeon.random.nbt.StructureNbt.PaletteEntry;

public final class CaptureService {

    public static final String EDIT_WORLD_PREFIX = "emd_edit_";

    public static final class Session {

        public int[] pos1;
        public int[] pos2;
        public String worldName = "";
        public UUID playerId;
        public Block pendingBlock;
        public final List<org.bukkit.entity.Entity> markers = new ArrayList<>();

        public int[] nodeWorldPos(ModuleNode node) {
            int[] anchorPos = {0, 0, 0};
            if (node.anchorId() != null && !node.anchorId().isBlank()) {
                for (DoorAnchor anchor : anchors) {
                    if (anchor.id().equals(node.anchorId())) {
                        anchorPos = new int[] {anchor.x(), anchor.y(), anchor.z()};
                        break;
                    }
                }
            }
            return new int[] {(int) Math.round(node.dx()) + anchorPos[0],
                    (int) Math.round(node.dy()) + anchorPos[1],
                    (int) Math.round(node.dz()) + anchorPos[2]};
        }
        public String structureType = TemplateModel.TYPE_ROOM;
        public final List<String> tags = new ArrayList<>();
        public int floors = 1;
        public int weight = 1;
        public String targetId;
        public boolean rotatable = true;
        public boolean mirrorable;
        public String intrusionPolicy = TemplateModel.INTRUSION_ALLOW;
        public String doorPolicy = TemplateModel.POLICY_KEEP;
        public String sealBlock = "";
        public String pendingConnectionKind = "";
        public Integer pendingFloorDelta;
        public Integer pendingVerticalDelta;
        public final List<DoorAnchor> anchors = new ArrayList<>();
        public final List<ModuleNode> nodes = new ArrayList<>();
    }

    public record Result(boolean ok, String messageKey, Map<String, String> params) {

        public static Result fail(String key, Map<String, String> params) {
            return new Result(false, key, params);
        }

        public static Result ok(String key, Map<String, String> params) {
            return new Result(true, key, params);
        }
    }

    public record DoorSettings(String kind, Integer floorDelta, Integer verticalDelta) {

        public DoorAnchor apply(DoorAnchor anchor) {
            return new DoorAnchor(anchor.id(), anchor.x(), anchor.y(), anchor.z(), anchor.facing(),
                    anchor.width(), anchor.height(), anchor.baseY(), anchor.floor(), anchor.policy(),
                    anchor.sealBlock(), kind, floorDelta, verticalDelta);
        }
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final java.util.function.Function<String, TemplateStore> storeProvider;

    public CaptureService(java.util.function.Function<String, TemplateStore> storeProvider) {
        this.storeProvider = storeProvider;
    }

    private volatile java.util.function.Consumer<Session> refreshHook;

    private volatile emaki.jiuwu.craft.dungeon.editor.EditorModuleMarkers markerRenderer;

    public void bindRefreshHook(java.util.function.Consumer<Session> refreshHook) {
        this.refreshHook = refreshHook;
    }

    public void bindMarkerRenderer(emaki.jiuwu.craft.dungeon.editor.EditorModuleMarkers markerRenderer) {
        this.markerRenderer = markerRenderer;
    }

    public void fireRefresh(UUID playerId) {
        java.util.function.Consumer<Session> hook = refreshHook;
        Session session = playerId == null ? null : sessions.get(playerId);
        if (hook != null && session != null) {
            hook.accept(session);
        }
    }

    public boolean hasSession(UUID player) {
        return sessions.containsKey(player);
    }

    public Session sessionOf(UUID player) {
        return sessions.get(player);
    }

    public Result start(Player player) {
        World world = player.getWorld();
        return startIn(player, world == null ? "" : world.getName());
    }

    public Result startIn(Player player, String worldName) {
        if (sessions.containsKey(player.getUniqueId())) {
            Session existing = sessions.get(player.getUniqueId());
            if (existing.worldName.equals(worldName)) {
                return Result.fail("random.template.already_open", Map.of());
            }
            discardSession(existing.playerId);
        }
        if (worldName == null || !worldName.startsWith(EDIT_WORLD_PREFIX)) {
            return Result.fail("random.template.world_forbidden", Map.of());
        }
        Session session = new Session();
        session.worldName = worldName;
        session.playerId = player.getUniqueId();
        sessions.put(player.getUniqueId(), session);
        return Result.ok("random.template.started", Map.of("world", worldName));
    }

    public void discardSession(UUID playerId) {
        Session removed = playerId == null ? null : sessions.remove(playerId);
        if (removed != null) {
            clearMarkersOf(removed);
        }
    }

    private void clearMarkersOf(Session removed) {
        EditorModuleMarkers renderer = markerRenderer;
        if (renderer != null) {
            renderer.removeAll(removed.markers);
            renderer.clearLabels(EditorModuleMarkers.captureLabelGroup(removed.playerId));
        } else {
            EditorModuleMarkers.removeAllNow(removed.markers);
        }
    }

    public Result pos(Player player, int which, RandomConfig config) {
        try {
            return posInner(player, which, config);
        } finally {
            fireRefresh(player.getUniqueId());
        }
    }

    Result posInner(Player player, int which, RandomConfig config) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return Result.fail("random.template.no_session", Map.of());
        }
        Block block = player.getTargetBlockExact(12);
        if (block == null) {
            return Result.fail("random.template.no_target", Map.of());
        }
        int[] point = {block.getX(), block.getY(), block.getZ()};
        String dungeonId = dungeonOf(session);
        String hit = dungeonId.isBlank() ? null : containing(dungeonId, point);
        if (which == 1) {
            session.pos1 = point;
        } else {
            session.pos2 = point;
        }
        String coord = point[0] + "," + point[1] + "," + point[2];
        if (session.pos1 == null || session.pos2 == null) {
            if (hit != null) {
                session.targetId = hit;
            }
            return hit == null
                    ? Result.ok("random.template.pos_set_new", Map.of("which", String.valueOf(which),
                            "coord", coord))
                    : Result.ok("random.template.pos_set_door", Map.of("which", String.valueOf(which),
                            "coord", coord, "target", hit));
        }
        String target = dungeonId.isBlank() ? null : containing(dungeonId, session.pos1, session.pos2);
        if (target != null) {
            return createDoor(player, session, target);
        }
        String overlap = dungeonId.isBlank() ? null : overlapping(dungeonId, session.pos1, session.pos2);
        if (overlap != null) {
            clearSelection(session);
            return Result.fail("random.region_overlap", Map.of("target", overlap));
        }
        return createRegion(player, session.structureType, config);
    }

    public Result posClear(Player player) {
        try {
            Session session = sessions.get(player.getUniqueId());
            if (session == null) {
                return Result.fail("random.template.no_session", Map.of());
            }
            clearSelection(session);
            session.pendingBlock = null;
            return Result.ok("random.template.pos_cleared", Map.of());
        } finally {
            fireRefresh(player.getUniqueId());
        }
    }

    public Result meta(Player player, String key, String value) {
        try {
            return metaInner(player, key, value);
        } finally {
            fireRefresh(player.getUniqueId());
        }
    }

    Result metaInner(Player player, String key, String value) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return Result.fail("random.template.no_session", Map.of());
        }
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        try {
            switch (normalized) {
                case "type" -> {
                    if (!TemplateModel.TYPE_ROOM.equals(value) && !TemplateModel.TYPE_CORRIDOR.equals(value)
                            && !TemplateModel.TYPE_TRANSITION.equals(value)) {
                        return Result.fail("random.template.meta_invalid", Map.of("key", key, "value", value));
                    }
                    // 已选中某个模板时, 改类型必须落到该模板上; 只改会话字段会让玩家看不到效果
                    String target = targetOf(session);
                    session.structureType = value;
                    if (target == null) {
                        return Result.ok("random.template.meta_set", Map.of("key", key, "value", value));
                    }
                    return retypeStructureInner(session, target, value);
                }
                case "tags" -> {
                    session.tags.clear();
                    for (String tag : value.split(",")) {
                        if (!tag.isBlank()) {
                            session.tags.add(tag.trim());
                        }
                    }
                }
                case "floors" -> {
                    return Result.fail("random.template.meta_derived", Map.of("key", "floors"));
                }
                case "weight" -> session.weight = Math.max(0, Integer.parseInt(value.trim()));
                case "mirrorable" -> session.mirrorable = Boolean.parseBoolean(value.trim());
                case "intrusion-policy" -> session.intrusionPolicy = TemplateModel.normalizeIntrusion(value);
                case "door-policy" -> session.doorPolicy = TemplateModel.normalizePolicy(value);
                case "seal-block" -> session.sealBlock = value.trim();
                case "rotatable" -> session.rotatable = Boolean.parseBoolean(value.trim());
                default -> {
                    return Result.fail("random.template.meta_unknown", Map.of("key", key));
                }
            }
        } catch (NumberFormatException exception) {
            return Result.fail("random.template.meta_invalid", Map.of("key", key, "value", value));
        }
        return Result.ok("random.template.meta_set", Map.of("key", key, "value", value));
    }

    public Result doorRemove(Player player, String id) {
        try {
            return doorRemoveInner(player, id);
        } finally {
            fireRefresh(player.getUniqueId());
        }
    }

    Result doorRemoveInner(Player player, String id) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return Result.fail("random.template.no_session", Map.of());
        }
        if (session.anchors.removeIf(anchor -> anchor.id().equals(id))) {
            return Result.ok("random.template.door_removed", Map.of("id", id,
                    "target", session.targetId == null ? "" : session.targetId));
        }
        String dungeonId = dungeonOf(session);
        if (dungeonId.isBlank()) {
            return Result.fail("random.template.door_unknown", Map.of("id", id));
        }
        for (Map.Entry<String, TemplateMeta> entry : storeProvider.apply(dungeonId).loadAll().entrySet()) {
            TemplateMeta meta = entry.getValue();
            List<DoorAnchor> kept = new ArrayList<>();
            for (DoorAnchor anchor : meta.anchors()) {
                if (!anchor.id().equals(id)) {
                    kept.add(anchor);
                }
            }
            if (kept.size() == meta.anchors().size()) {
                continue;
            }
            if (!saveStructure(session, meta.withAnchors(withDerivedFloors(kept)))) {
                return Result.fail("random.template.save_failed", Map.of("error", "template_write"));
            }
            session.targetId = entry.getKey();
            return Result.ok("random.template.door_removed", Map.of("id", id, "target", entry.getKey()));
        }
        return Result.fail("random.template.door_unknown", Map.of("id", id));
    }

    public String targetOf(Session session) {
        if (session == null) {
            return null;
        }
        String dungeonId = dungeonOf(session);
        if (dungeonId.isBlank()) {
            return null;
        }
        if (session.pos1 != null && session.pos2 != null) {
            String hit = containing(dungeonId, session.pos1, session.pos2);
            if (hit != null) {
                return hit;
            }
        }
        int[] single = session.pos1 != null ? session.pos1 : session.pos2;
        if (single != null) {
            String hit = containing(dungeonId, single);
            if (hit != null) {
                return hit;
            }
        }
        if (session.targetId != null && storeProvider.apply(dungeonId).exists(session.targetId)) {
            return session.targetId;
        }
        return null;
    }

    public DoorSettings pendingDoorSettings(Session session) {
        return new DoorSettings(session.pendingConnectionKind, session.pendingFloorDelta,
                session.pendingVerticalDelta);
    }

    public void pendingDoorSettings(Session session, DoorSettings settings) {
        session.pendingConnectionKind = settings.kind();
        session.pendingFloorDelta = settings.floorDelta();
        session.pendingVerticalDelta = settings.verticalDelta();
    }

    public boolean updateDoor(Session session, String templateId, String doorId, DoorSettings settings) {
        TemplateMeta meta = structure(session, templateId);
        if (meta == null) {
            return false;
        }
        List<DoorAnchor> anchors = new ArrayList<>();
        boolean found = false;
        for (DoorAnchor anchor : meta.anchors()) {
            anchors.add(anchor.id().equals(doorId) ? settings.apply(anchor) : anchor);
            found |= anchor.id().equals(doorId);
        }
        return found && saveStructure(session, meta.withAnchors(withDerivedFloors(anchors)));
    }

    public List<DoorAnchor> doorsOf(Session session) {
        if (session == null) {
            return List.of();
        }
        String target = targetOf(session);
        String dungeonId = dungeonOf(session);
        if (target != null && !dungeonId.isBlank()) {
            TemplateMeta meta = storeProvider.apply(dungeonId).loadMeta(target);
            if (meta != null) {
                return meta.anchors();
            }
        }
        return session.anchors;
    }

    private String containing(String dungeonId, int[] point) {
        return containing(dungeonId, point, null);
    }

    private String containing(String dungeonId, int[] first, int[] second) {
        for (Map.Entry<String, TemplateMeta> entry : storeProvider.apply(dungeonId).loadAll().entrySet()) {
            List<Integer> sel = entry.getValue().selection();
            if (sel.size() != 6) {
                continue;
            }
            if (inside(sel, first) && (second == null || inside(sel, second))) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String overlapping(String dungeonId, int[] first, int[] second) {
        int minX = Math.min(first[0], second[0]);
        int minY = Math.min(first[1], second[1]);
        int minZ = Math.min(first[2], second[2]);
        int maxX = Math.max(first[0], second[0]);
        int maxY = Math.max(first[1], second[1]);
        int maxZ = Math.max(first[2], second[2]);
        for (Map.Entry<String, TemplateMeta> entry : storeProvider.apply(dungeonId).loadAll().entrySet()) {
            List<Integer> sel = entry.getValue().selection();
            if (sel.size() != 6) {
                continue;
            }
            int selMinX = Math.min(sel.get(0), sel.get(3));
            int selMinY = Math.min(sel.get(1), sel.get(4));
            int selMinZ = Math.min(sel.get(2), sel.get(5));
            int selMaxX = Math.max(sel.get(0), sel.get(3));
            int selMaxY = Math.max(sel.get(1), sel.get(4));
            int selMaxZ = Math.max(sel.get(2), sel.get(5));
            boolean disjoint = maxX < selMinX || minX > selMaxX
                    || maxY < selMinY || minY > selMaxY
                    || maxZ < selMinZ || minZ > selMaxZ;
            if (!disjoint) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean inside(List<Integer> sel, int[] point) {
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

    private Result createDoor(Player player, Session session, String targetId) {
        String dungeonId = dungeonOf(session);
        if (dungeonId.isBlank()) {
            return Result.fail("random.template.world_forbidden", Map.of());
        }
        TemplateMeta meta = storeProvider.apply(dungeonId).loadMeta(targetId);
        List<Integer> sel = meta == null ? List.<Integer>of() : meta.selection();
        if (sel.size() != 6) {
            return Result.fail("random.template.door_no_target", Map.of());
        }
        int minX = Math.min(sel.get(0), sel.get(3));
        int minY = Math.min(sel.get(1), sel.get(4));
        int minZ = Math.min(sel.get(2), sel.get(5));
        int maxX = Math.max(sel.get(0), sel.get(3));
        int maxY = Math.max(sel.get(1), sel.get(4));
        int maxZ = Math.max(sel.get(2), sel.get(5));
        if (!inside(sel, session.pos1) || !inside(sel, session.pos2)) {
            return Result.fail("random.template.door_not_surface", Map.of("target", targetId));
        }
        int[] a = session.pos1;
        int[] b = session.pos2;
        int lowX = Math.min(a[0], b[0]);
        int highX = Math.max(a[0], b[0]);
        int lowY = Math.min(a[1], b[1]);
        int highY = Math.max(a[1], b[1]);
        int lowZ = Math.min(a[2], b[2]);
        int highZ = Math.max(a[2], b[2]);
        String facing;
        int doorX;
        int doorY = lowY;
        int doorZ;
        int width;
        int height = highY - lowY + 1;
        if (lowX == highX && (lowX == minX || lowX == maxX)) {
            facing = lowX == maxX ? TemplateModel.FACING_E : TemplateModel.FACING_W;
            doorX = lowX;
            doorZ = lowZ;
            width = highZ - lowZ + 1;
        } else if (lowZ == highZ && (lowZ == minZ || lowZ == maxZ)) {
            facing = lowZ == maxZ ? TemplateModel.FACING_S : TemplateModel.FACING_N;
            doorX = lowX;
            doorZ = lowZ;
            width = highX - lowX + 1;
        } else if (lowY == highY && (lowY == minY || lowY == maxY)) {
            facing = lowY == maxY ? TemplateModel.FACING_UP : TemplateModel.FACING_DOWN;
            doorX = lowX;
            doorZ = lowZ;
            width = highX - lowX + 1;
            height = highZ - lowZ + 1;
        } else {
            return Result.fail("random.template.door_not_surface", Map.of("target", targetId));
        }
        DoorSettings settings = pendingDoorSettings(session);
        String id = nextDoorId(meta);
        DoorAnchor worldAnchor = settings.apply(new DoorAnchor(id, doorX, doorY, doorZ, facing,
                width, height, doorY, 1, "", meta.sealBlock()));
        Result check = validateAnchor(worldAnchor, minX, minY, minZ, maxX, maxY, maxZ,
                meta.doorPolicy(), meta.sealBlock());
        if (check != null) {
            return check;
        }
        String doorOverlap = overlappingDoor(meta, id, worldAnchor);
        if (doorOverlap != null) {
            clearSelection(session);
            return Result.fail("random.template.door_overlap", Map.of("id", doorOverlap));
        }
        DoorAnchor anchor = settings.apply(new DoorAnchor(id, doorX - minX, doorY - minY, doorZ - minZ,
                facing, width, height, doorY - minY, 1, "", meta.sealBlock()));
        List<DoorAnchor> anchors = new ArrayList<>(meta.anchors());
        anchors.add(anchor);
        anchors = withDerivedFloors(anchors);
        if (!saveStructure(session, meta.withAnchors(anchors))) {
            return Result.fail("random.template.save_failed", Map.of("error", "template_write"));
        }
        session.targetId = targetId;
        session.pos1 = null;
        session.pos2 = null;
        session.pendingConnectionKind = "";
        session.pendingFloorDelta = null;
        session.pendingVerticalDelta = null;
        return Result.ok("random.template.door_created", Map.of("id", id, "target", targetId,
                "facing", facing, "width", String.valueOf(width), "height", String.valueOf(height)));
    }

    /**
     * 新门与已存门的格位重叠检测。
     *
     * <p>门是贴在选区侧面的平面开口, 按 facing 展开成宽 x 高的格子集合后逐格比较,
     * 任一格位重叠即拒绝。与房间/走廊的 AABB 重叠口径一致: 选区不得包含任何已存门结构。
     *
     * @return 冲突的已存门 id, 无冲突时返回 null
     */
    private String overlappingDoor(TemplateMeta meta, String selfId, DoorAnchor worldAnchor) {
        List<int[]> cells = doorCells(worldAnchor);
        for (DoorAnchor existing : meta.anchors()) {
            if (existing.id().equals(selfId)) {
                continue;
            }
            List<int[]> occupied = doorCells(absoluteAnchor(meta, existing));
            for (int[] cell : cells) {
                for (int[] taken : occupied) {
                    if (cell[0] == taken[0] && cell[1] == taken[1] && cell[2] == taken[2]) {
                        return existing.id();
                    }
                }
            }
        }
        return null;
    }

    /** 模板局部锚点换算为编辑世界绝对坐标。 */
    private static int countDoorFloors(List<DoorAnchor> anchors) {
        java.util.TreeSet<Integer> heights = new java.util.TreeSet<>();
        for (DoorAnchor door : anchors) {
            heights.add(door.y());
        }
        return Math.max(1, heights.size());
    }

    private List<DoorAnchor> withDerivedFloors(List<DoorAnchor> anchors) {
        java.util.TreeSet<Integer> heights = new java.util.TreeSet<>();
        for (DoorAnchor door : anchors) {
            heights.add(door.y());
        }
        Map<Integer, Integer> levels = new LinkedHashMap<>();
        int level = 1;
        for (Integer height : heights) {
            levels.put(height, level++);
        }
        List<DoorAnchor> result = new ArrayList<>(anchors.size());
        for (DoorAnchor door : anchors) {
            int floor = levels.getOrDefault(door.y(), 1);
            result.add(new DoorAnchor(door.id(), door.x(), door.y(), door.z(), door.facing(),
                    door.width(), door.height(), door.baseY(), floor, door.policy(), door.sealBlock(),
                    door.connectionKind(), door.floorDelta(), door.verticalDelta()));
        }
        return result;
    }

    private DoorAnchor absoluteAnchor(TemplateMeta meta, DoorAnchor anchor) {
        int[] min = meta.selectionMin();
        return new DoorAnchor(anchor.id(), min[0] + anchor.x(), min[1] + anchor.y(), min[2] + anchor.z(),
                anchor.facing(), anchor.width(), anchor.height(), min[1] + anchor.baseY(),
                anchor.floor(), anchor.policy(), anchor.sealBlock(), anchor.connectionKind(),
                anchor.floorDelta(), anchor.verticalDelta());
    }

    /** 门占据的格子集合: 沿门宽方向铺开, 沿 Y 方向堆叠。 */
    private List<int[]> doorCells(DoorAnchor worldAnchor) {
        return new ArrayList<>(AnchorGeometry.openingCells(worldAnchor));
    }

    private String nextDoorId(TemplateMeta meta) {
        int max = 0;
        for (DoorAnchor anchor : meta.anchors()) {
            String id = anchor.id();
            if (!id.startsWith("door_")) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(id.substring(5)));
            } catch (NumberFormatException ignored) {
            }
        }
        return "door_" + (max + 1);
    }

    public Result nodeAdd(Player player, String module, String nodeId, String anchorId) {
        return nodeAdd(player, module, nodeId, anchorId, null);
    }

    public Result nodeAdd(Player player, String module, String nodeId, String anchorId, Block target) {
        try {
            return nodeAddInner(player, module, nodeId, anchorId, target);
        } finally {
            fireRefresh(player.getUniqueId());
        }
    }

    Result nodeAddInner(Player player, String module, String nodeId, String anchorId, Block target) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return Result.fail("random.template.no_session", Map.of());
        }
        if ("spawn_point".equals(module)) {
            for (ModuleNode existing : session.nodes) {
                if ("spawn_point".equals(existing.moduleType())) {
                    return Result.fail("random.template.node_single", Map.of("module", module));
                }
            }
        }
        Block block = target != null ? target : player.getTargetBlockExact(12);
        if (block == null) {
            return Result.fail("random.template.no_target", Map.of());
        }
        int[] anchorPos = {0, 0, 0};
        if (anchorId != null && !anchorId.isBlank()) {
            DoorAnchor anchor = session.anchors.stream()
                    .filter(entry -> entry.id().equals(anchorId))
                    .findFirst()
                    .orElse(null);
            if (anchor == null) {
                return Result.fail("random.template.anchor_unknown", Map.of("id", anchorId));
            }
            anchorPos = new int[] {anchor.x(), anchor.y(), anchor.z()};
        }
        Map<String, Object> params = new LinkedHashMap<>();
        if ("tip_points".equals(module)) {
            params.put("type", "message");
            params.put("text", "");
        }
        session.nodes.add(new ModuleNode(module, nodeId,
                anchorId == null ? "" : anchorId,
                block.getX() - anchorPos[0], block.getY() - anchorPos[1], block.getZ() - anchorPos[2],
                0.0D, params));
        return Result.ok("random.template.node_added", Map.of("module", module, "id", nodeId));
    }

    public Result nodeAddToStructure(Player player, String templateId, String module, String nodeId,
            String anchorId, Block target) {
        try {
            return nodeAddToStructureInner(player, templateId, module, nodeId, anchorId, target);
        } finally {
            fireRefresh(player.getUniqueId());
        }
    }

    Result nodeAddToStructureInner(Player player, String templateId, String module, String nodeId,
            String anchorId, Block target) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return Result.fail("random.template.no_session", Map.of());
        }
        TemplateMeta meta = structure(session, templateId);
        if ("spawn_point".equals(module)) {
            for (ModuleNode existing : meta.nodes()) {
                if ("spawn_point".equals(existing.moduleType())) {
                    return Result.fail("random.template.node_single", Map.of("module", module));
                }
            }
        }
        if (meta == null) {
            return Result.fail("random.gui.structure_missing", Map.of("id", templateId));
        }
        Block block = target != null ? target : player.getTargetBlockExact(12);
        if (block == null) {
            return Result.fail("random.template.no_target", Map.of());
        }
        DoorAnchor anchor = anchorId == null || anchorId.isBlank() ? null : meta.anchor(anchorId);
        if (anchorId != null && !anchorId.isBlank() && anchor == null) {
            return Result.fail("random.template.anchor_unknown", Map.of("id", anchorId));
        }
        int[] selMin = meta.selectionMin();
        int anchorX = anchor == null ? 0 : anchor.x();
        int anchorY = anchor == null ? 0 : anchor.y();
        int anchorZ = anchor == null ? 0 : anchor.z();
        Map<String, Object> params = new LinkedHashMap<>();
        if ("tip_points".equals(module)) {
            params.put("type", "message");
            params.put("text", "");
        }
        List<ModuleNode> nodes = new ArrayList<>(meta.nodes());
        nodes.add(new ModuleNode(module, nodeId, anchor == null ? "" : anchor.id(),
                block.getX() - selMin[0] - anchorX,
                block.getY() - selMin[1] - anchorY,
                block.getZ() - selMin[2] - anchorZ,
                0.0D, params));
        if (!saveStructure(session, new TemplateMeta(meta.id(), meta.templateRef(), meta.structureType(),
                meta.tags(), meta.sizeX(), meta.sizeY(), meta.sizeZ(), meta.floors(), meta.anchors(),
                meta.rotatable(), meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(),
                meta.sealBlock(), nodes, meta.mask(), meta.entities(), meta.selection(), meta.weight()))) {
            return Result.fail("random.gui.structure_save_failed", Map.of("id", templateId));
        }
        return Result.ok("random.template.node_added", Map.of("module", module, "id", nodeId));
    }

    public Result nodeParam(Player player, String nodeId, String key, String value) {
        try {
            return nodeParamInner(player, nodeId, key, value);
        } finally {
            fireRefresh(player.getUniqueId());
        }
    }

    Result nodeParamInner(Player player, String nodeId, String key, String value) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return Result.fail("random.template.no_session", Map.of());
        }
        for (int i = 0; i < session.nodes.size(); i++) {
            ModuleNode node = session.nodes.get(i);
            if (node.nodeId().equals(nodeId)) {
                Map<String, Object> params = new LinkedHashMap<>(node.params());
                params.put(key, value);
                session.nodes.set(i, new ModuleNode(node.moduleType(), node.nodeId(), node.anchorId(),
                        node.dx(), node.dy(), node.dz(), node.yaw(), params));
                return Result.ok("random.template.node_param_set", Map.of("id", nodeId, "key", key));
            }
        }
        return Result.fail("random.template.node_unknown", Map.of("id", nodeId));
    }

    public Result save(Player player, String typeId, RandomConfig config, boolean confirmed) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return Result.fail("random.template.no_session", Map.of());
        }
        if (session.pos1 == null || session.pos2 == null) {
            return Result.fail("random.template.pos_missing", Map.of());
        }
        String normalizedId = typeId == null ? "" : typeId.trim().toLowerCase(Locale.ROOT);
        if (normalizedId.isBlank() || !normalizedId.matches("[a-z0-9_-]+")) {
            return Result.fail("random.template.id_invalid", Map.of("id", String.valueOf(typeId)));
        }
        World world = player.getWorld();
        String dungeonId = world == null || !world.getName().startsWith(EDIT_WORLD_PREFIX)
                ? "" : world.getName().substring(EDIT_WORLD_PREFIX.length());
        if (dungeonId.isBlank()) {
            return Result.fail("random.template.world_forbidden", Map.of());
        }
        TemplateStore store = storeProvider.apply(dungeonId);
        if (store.exists(normalizedId) && !confirmed) {
            if (config != null && "confirm".equals(config.editor.overwritePolicy())) {
                return Result.fail("random.template.overwrite_confirm", Map.of("id", normalizedId));
            }
            return Result.fail("random.template.overwrite_denied", Map.of("id", normalizedId));
        }
        int minX = Math.min(session.pos1[0], session.pos2[0]);
        int minY = Math.min(session.pos1[1], session.pos2[1]);
        int minZ = Math.min(session.pos1[2], session.pos2[2]);
        int maxX = Math.max(session.pos1[0], session.pos2[0]);
        int maxY = Math.max(session.pos1[1], session.pos2[1]);
        int maxZ = Math.max(session.pos1[2], session.pos2[2]);
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > 2_000_000L) {
            return Result.fail("random.template.too_large", Map.of("id", normalizedId));
        }
        for (DoorAnchor anchor : session.anchors) {
            Result anchorCheck = validateAnchor(anchor, minX, minY, minZ, maxX, maxY, maxZ,
                    session.doorPolicy, session.sealBlock);
            if (anchorCheck != null) {
                return anchorCheck;
            }
        }
        if (TemplateModel.POLICY_SEAL.equals(session.doorPolicy) && session.sealBlock.isBlank()) {
            return Result.fail("random.template.seal_missing", Map.of("id", normalizedId));
        }
        if (!session.sealBlock.isBlank() && Material.matchMaterial(session.sealBlock) == null) {
            return Result.fail("random.template.seal_unresolved", Map.of("block", session.sealBlock));
        }
        Mask mask = new Mask(sizeX, sizeY, sizeZ);
        Map<String, Integer> paletteIndex = new LinkedHashMap<>();
        List<PaletteEntry> palette = new ArrayList<>();
        List<BlockEntry> blocks = new ArrayList<>();
        long blockCount = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockData data = world.getBlockAt(x, y, z).getBlockData();
                    if (data.getMaterial().isAir()) {
                        continue;
                    }
                    blockCount++;
                    mask.add(x - minX, y - minY, z - minZ);
                    String key = data.getAsString();
                    Integer state = paletteIndex.get(key);
                    if (state == null) {
                        state = palette.size();
                        paletteIndex.put(key, state);
                        palette.add(PaletteEntry.parse(key));
                    }
                    blocks.add(new BlockEntry(x - minX, y - minY, z - minZ, state));
                }
            }
        }
        if (config != null && blockCount > config.limits.maxTemplateBlocks()) {
            return Result.fail("random.template.too_many_blocks", Map.of("id", normalizedId));
        }
        StructureNbt.StructureData data = new StructureNbt.StructureData(sizeX, sizeY, sizeZ, palette, blocks);
        List<Map<String, Object>> entities = new ArrayList<>();
        if (config != null && config.editor.allowEntityCapture()) {
            for (var entity : world.getEntities()) {
                if (!(entity instanceof org.bukkit.entity.LivingEntity living)) {
                    continue;
                }
                var location = living.getLocation();
                if (location.getX() < minX || location.getX() > maxX + 1
                        || location.getY() < minY || location.getY() > maxY + 1
                        || location.getZ() < minZ || location.getZ() > maxZ + 1) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", living.getType().name());
                entry.put("x", location.getX() - minX);
                entry.put("y", location.getY() - minY);
                entry.put("z", location.getZ() - minZ);
                entry.put("yaw", (double) location.getYaw());
                net.kyori.adventure.text.Component customName = living.customName();
                if (customName != null) {
                    entry.put("name", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(customName));
                }
                entities.add(entry);
            }
        }
        List<DoorAnchor> localAnchors = new ArrayList<>(session.anchors.size());
        for (DoorAnchor anchor : session.anchors) {
            localAnchors.add(new DoorAnchor(anchor.id(), anchor.x() - minX, anchor.y() - minY, anchor.z() - minZ,
                    anchor.facing(), anchor.width(), anchor.height(), anchor.baseY() - minY,
                    anchor.floor(), anchor.policy(), anchor.sealBlock(), anchor.connectionKind(),
                    anchor.floorDelta(), anchor.verticalDelta()));
        }
        List<ModuleNode> localNodes = new ArrayList<>(session.nodes.size());
        for (ModuleNode node : session.nodes) {
            boolean noAnchor = node.anchorId() == null || node.anchorId().isBlank();
            localNodes.add(new ModuleNode(node.moduleType(), node.nodeId(), node.anchorId(),
                    noAnchor ? node.dx() - minX : node.dx(),
                    noAnchor ? node.dy() - minY : node.dy(),
                    noAnchor ? node.dz() - minZ : node.dz(),
                    node.yaw(), node.params()));
        }
        TemplateMeta meta = new TemplateMeta(normalizedId, normalizedId, session.structureType,
                List.copyOf(session.tags), sizeX, sizeY, sizeZ, countDoorFloors(localAnchors),
                List.copyOf(localAnchors), session.rotatable, session.mirrorable,
                session.intrusionPolicy, session.doorPolicy, session.sealBlock,
                List.copyOf(localNodes), mask, entities,
                List.of(minX, minY, minZ, maxX, maxY, maxZ), session.weight);
        try {
            store.save(normalizedId, meta, data);
        } catch (IOException exception) {
            return Result.fail("random.template.save_failed", Map.of("error",
                    String.valueOf(exception.getMessage())));
        }
        Result saved = Result.ok("random.template.saved", Map.of(
                "id", normalizedId,
                "blocks", String.valueOf(blockCount),
                "anchors", String.valueOf(session.anchors.size())));
        resetForNewRegion(session);
        fireRefresh(player.getUniqueId());
        return saved;
    }

    public Result createRegion(Player player, String structureType, RandomConfig config) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return Result.fail("random.template.no_session", Map.of());
        }
        String normalizedType = structureType == null ? "" : structureType.trim().toLowerCase(Locale.ROOT);
        if (!TemplateModel.TYPE_ROOM.equals(normalizedType) && !TemplateModel.TYPE_CORRIDOR.equals(normalizedType)
                && !TemplateModel.TYPE_TRANSITION.equals(normalizedType)) {
            return Result.fail("random.region_type_invalid", Map.of("type", String.valueOf(structureType)));
        }
        World world = player.getWorld();
        String dungeonId = world == null || !world.getName().startsWith(EDIT_WORLD_PREFIX)
                ? "" : world.getName().substring(EDIT_WORLD_PREFIX.length());
        if (dungeonId.isBlank()) {
            return Result.fail("random.template.world_forbidden", Map.of());
        }
        session.structureType = normalizedType;
        String regionId = nextRegionId(dungeonId, normalizedType);
        Result saved = save(player, regionId, config, true);
        if (!saved.ok()) {
            return saved;
        }
        session.targetId = regionId;
        Map<String, String> params = new LinkedHashMap<>(saved.params());
        params.put("type", normalizedType);
        return Result.ok("random.region_created", params);
    }

    public Result retypeStructure(Player player, String templateId, String nextType) {
        try {
            Session session = sessions.get(player.getUniqueId());
            if (session == null) {
                return Result.fail("random.template.no_session", Map.of());
            }
            return retypeStructureInner(session, templateId, nextType);
        } finally {
            fireRefresh(player.getUniqueId());
        }
    }

    Result retypeStructureInner(Session session, String templateId, String nextType) {
        {
            String normalizedType = nextType == null ? ""
                    : nextType.trim().toLowerCase(Locale.ROOT);
            if (!TemplateModel.TYPE_ROOM.equals(normalizedType)
                    && !TemplateModel.TYPE_CORRIDOR.equals(normalizedType)
                    && !TemplateModel.TYPE_TRANSITION.equals(normalizedType)) {
                return Result.fail("random.region_type_invalid", Map.of("type", String.valueOf(nextType)));
            }
            TemplateMeta meta = structure(session, templateId);
            if (meta == null) {
                return Result.fail("random.gui.structure_missing", Map.of("id", templateId));
            }
            if (normalizedType.equals(meta.structureType())) {
                return Result.ok("random.gui.structure_saved", Map.of("id", templateId));
            }
            String dungeonId = dungeonOf(session);
            if (dungeonId.isBlank()) {
                return Result.fail("random.template.world_forbidden", Map.of());
            }
            String renamedId = nextRegionId(dungeonId, normalizedType);
            TemplateMeta renamed = new TemplateMeta(renamedId, renamedId, normalizedType, meta.tags(),
                    meta.sizeX(), meta.sizeY(), meta.sizeZ(), meta.floors(), meta.anchors(),
                    meta.rotatable(), meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(),
                    meta.sealBlock(), meta.nodes(), meta.mask(), meta.entities(), meta.selection(),
                    meta.weight());
            TemplateStore store = storeProvider.apply(dungeonId);
            StructureNbt.StructureData data = store.loadTemplate(templateId);
            if (data == null) {
                return Result.fail("random.gui.structure_save_failed", Map.of("id", templateId));
            }
            try {
                store.save(renamedId, renamed, data);
            } catch (IOException exception) {
                return Result.fail("random.gui.structure_save_failed", Map.of("id", renamedId));
            }
            deleteStructure(session, templateId);
            session.targetId = renamedId;
            return Result.ok("random.region_retyped", Map.of("id", renamedId, "type", normalizedType));
        }
    }

    /**
     * 新建结构的编号。
     *
     * <p>取最小可用空闲号, 而不是"当前最大值 + 1": 删掉 room_1 后新建应当重新得到 room_1,
     * 否则编号会随着删除/新建不断空洞化。
     */
    private String nextRegionId(String dungeonId, String prefix) {
        TemplateStore store = storeProvider.apply(dungeonId);
        java.util.Set<String> used = new java.util.HashSet<>(store.ids());
        for (int index = 1; index < 100_000; index++) {
            String candidate = prefix + "_" + index;
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return prefix + "_" + System.currentTimeMillis();
    }

    /**
     * 按模板自身选区重新采集方块。
     *
     * <p>编辑器保存后调用: 玩家框选后修改了结构内容, 但模板里存的是上次采集的方块快照,
     * 若不重采, 模板与编辑世界里的实际结构会一直不一致。使用模板记录的 selection 而不是
     * 当前会话选区, 因为会话选区在保存后已被清空。
     *
     * @return 成功重采的模板数
     */
    public int recaptureStructures(Player player, String dungeonId, RandomConfig config) {
        if (dungeonId == null || dungeonId.isBlank()) {
            return 0;
        }
        World world = Bukkit.getWorld(EDIT_WORLD_PREFIX + dungeonId);
        if (world == null) {
            return 0;
        }
        TemplateStore store = storeProvider.apply(dungeonId);
        int recaptured = 0;
        for (Map.Entry<String, TemplateMeta> entry : store.loadAll().entrySet()) {
            TemplateMeta meta = entry.getValue();
            List<Integer> sel = meta.selection();
            if (sel.size() != 6) {
                continue;
            }
            int minX = Math.min(sel.get(0), sel.get(3));
            int minY = Math.min(sel.get(1), sel.get(4));
            int minZ = Math.min(sel.get(2), sel.get(5));
            int maxX = Math.max(sel.get(0), sel.get(3));
            int maxY = Math.max(sel.get(1), sel.get(4));
            int maxZ = Math.max(sel.get(2), sel.get(5));
            int sizeX = maxX - minX + 1;
            int sizeY = maxY - minY + 1;
            int sizeZ = maxZ - minZ + 1;
            if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
                continue;
            }
            long volume = (long) sizeX * sizeY * sizeZ;
            if (volume > 2_000_000L) {
                continue;
            }
            Mask mask = new Mask(sizeX, sizeY, sizeZ);
            Map<String, Integer> paletteIndex = new LinkedHashMap<>();
            List<PaletteEntry> palette = new ArrayList<>();
            List<BlockEntry> blocks = new ArrayList<>();
            long blockCount = 0;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockData data = world.getBlockAt(x, y, z).getBlockData();
                        if (data.getMaterial().isAir()) {
                            continue;
                        }
                        blockCount++;
                        mask.add(x - minX, y - minY, z - minZ);
                        String key = data.getAsString();
                        Integer state = paletteIndex.get(key);
                        if (state == null) {
                            state = palette.size();
                            paletteIndex.put(key, state);
                            palette.add(PaletteEntry.parse(key));
                        }
                        blocks.add(new BlockEntry(x - minX, y - minY, z - minZ, state));
                    }
                }
            }
            if (config != null && blockCount > config.limits.maxTemplateBlocks()) {
                continue;
            }
            StructureNbt.StructureData data = new StructureNbt.StructureData(sizeX, sizeY, sizeZ, palette, blocks);
            TemplateMeta refreshed = new TemplateMeta(meta.id(), meta.templateRef(), meta.structureType(),
                    meta.tags(), sizeX, sizeY, sizeZ, meta.floors(), meta.anchors(),
                    meta.rotatable(), meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(),
                    meta.sealBlock(), meta.nodes(), mask, meta.entities(), meta.selection(), meta.weight());
            try {
                store.save(meta.id(), refreshed, data);
                recaptured++;
            } catch (IOException exception) {
                FailureRecorder.recordIgnoredFailure("EmakiDungeon", "CaptureService.java#recapture", exception);
            }
        }
        if (recaptured > 0) {
            fireRefresh(player == null ? null : player.getUniqueId());
        }
        return recaptured;
    }

    private void clearSelection(Session session) {
        session.pos1 = null;
        session.pos2 = null;
    }

    private void resetForNewRegion(Session session) {
        session.pos1 = null;
        session.pos2 = null;
        session.pendingBlock = null;
        session.structureType = TemplateModel.TYPE_ROOM;
        session.tags.clear();
        session.floors = 1;
        session.rotatable = true;
        session.mirrorable = false;
        session.intrusionPolicy = TemplateModel.INTRUSION_ALLOW;
        session.doorPolicy = TemplateModel.POLICY_KEEP;
        session.sealBlock = "";
        session.anchors.clear();
        session.nodes.clear();
        session.targetId = null;
    }

    private Result validateAnchor(DoorAnchor anchor, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ, String doorPolicy, String sealBlock) {
        boolean onSurface = anchor.x() == minX || anchor.x() == maxX
                || anchor.y() == minY || anchor.y() == maxY
                || anchor.z() == minZ || anchor.z() == maxZ;
        if (!onSurface) {
            return Result.fail("random.template.anchor_not_surface", Map.of("id", anchor.id()));
        }
        int frontX = anchor.x() + TemplateModel.facingDx(anchor.facing());
        int frontY = anchor.y() + TemplateModel.facingDy(anchor.facing());
        int frontZ = anchor.z() + TemplateModel.facingDz(anchor.facing());
        boolean pointsOut = frontX < minX || frontX > maxX || frontY < minY || frontY > maxY
                || frontZ < minZ || frontZ > maxZ;
        if (!pointsOut) {
            return Result.fail("random.template.anchor_facing_in", Map.of("id", anchor.id()));
        }
        for (int[] cell : AnchorGeometry.openingCells(anchor)) {
            if (cell[0] < minX || cell[0] > maxX || cell[1] < minY || cell[1] > maxY
                    || cell[2] < minZ || cell[2] > maxZ) {
                return Result.fail("random.template.anchor_section_out", Map.of("id", anchor.id()));
            }
        }
        boolean seal = TemplateModel.POLICY_SEAL.equals(anchor.policyOrDefault(doorPolicy));
        if (seal && anchor.sealBlock().isBlank() && (sealBlock == null || sealBlock.isBlank())) {
            return Result.fail("random.template.seal_missing", Map.of("id", anchor.id()));
        }
        return null;
    }

    public record StructureHit(String templateId, String nodeId) {
    }

    public ModuleNode nodeAt(Session session, Block block) {
        for (ModuleNode node : session.nodes) {
            int[] anchorPos = anchorPos(session, node.anchorId());
            int bx = (int) Math.round(node.dx()) + anchorPos[0];
            int by = (int) Math.round(node.dy()) + anchorPos[1];
            int bz = (int) Math.round(node.dz()) + anchorPos[2];
            if (bx == block.getX() && by == block.getY() && bz == block.getZ()) {
                return node;
            }
        }
        return null;
    }

    public boolean deleteNode(Session session, String nodeId) {
        return session.nodes.removeIf(node -> node.nodeId().equals(nodeId));
    }

    public StructureHit structureAt(String dungeonId, Block block) {
        for (Map.Entry<String, TemplateMeta> entry : storeProvider.apply(dungeonId).loadAll().entrySet()) {
            TemplateMeta meta = entry.getValue();
            List<Integer> sel = meta.selection();
            if (sel.size() != 6) {
                continue;
            }
            int minX = Math.min(sel.get(0), sel.get(3));
            int minY = Math.min(sel.get(1), sel.get(4));
            int minZ = Math.min(sel.get(2), sel.get(5));
            int maxX = Math.max(sel.get(0), sel.get(3));
            int maxY = Math.max(sel.get(1), sel.get(4));
            int maxZ = Math.max(sel.get(2), sel.get(5));
            if (block.getX() < minX || block.getX() > maxX
                    || block.getY() < minY || block.getY() > maxY
                    || block.getZ() < minZ || block.getZ() > maxZ) {
                continue;
            }
            for (ModuleNode node : meta.nodes()) {
                int[] pos = meta.nodeAbsolute(node);
                if (pos[0] == block.getX() && pos[1] == block.getY() && pos[2] == block.getZ()) {
                    return new StructureHit(entry.getKey(), node.nodeId());
                }
            }
            return new StructureHit(entry.getKey(), null);
        }
        return null;
    }

    public TemplateMeta structure(Session session, String templateId) {
        return storeProvider.apply(dungeonOf(session)).loadMeta(templateId);
    }

    public boolean saveStructure(Session session, TemplateMeta meta) {
        TemplateStore store = storeProvider.apply(dungeonOf(session));
        StructureNbt.StructureData data = store.loadTemplate(meta.id());
        if (data == null) {
            return false;
        }
        try {
            store.save(meta.id(), meta, data);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    public Result structureNodeParam(Player player, String templateId, String nodeId,
            List<String> path, String value) {
        try {
            Session session = sessions.get(player.getUniqueId());
            if (session == null) {
                return Result.fail("random.template.no_session", Map.of());
            }
            TemplateMeta meta = structure(session, templateId);
            if (meta == null) {
                return Result.fail("random.gui.structure_missing", Map.of("id", templateId));
            }
            ModuleNode target = null;
            for (ModuleNode node : meta.nodes()) {
                if (node.nodeId().equals(nodeId)) {
                    target = node;
                    break;
                }
            }
            if (target == null) {
                return Result.fail("random.template.node_unknown", Map.of("id", nodeId));
            }
            Map<String, Object> params = new LinkedHashMap<>(target.params());
            applyParam(params, path, value);
            ModuleNode updated = new ModuleNode(target.moduleType(), target.nodeId(), target.anchorId(),
                    target.dx(), target.dy(), target.dz(), target.yaw(), params);
            if (!saveStructureNode(session, templateId, updated)) {
                return Result.fail("random.gui.structure_save_failed", Map.of("id", templateId));
            }
            return Result.ok("random.template.node_param_set", Map.of("id", nodeId,
                    "key", String.join(".", path)));
        } finally {
            fireRefresh(player.getUniqueId());
        }
    }

    @SuppressWarnings("unchecked")
    private void applyParam(Map<String, Object> params, List<String> path, String value) {
        if (path.isEmpty()) {
            return;
        }
        Map<String, Object> cursor = params;
        for (int index = 0; index < path.size() - 1; index++) {
            Object next = cursor.get(path.get(index));
            if (!(next instanceof Map<?, ?> map)) {
                Map<String, Object> created = new LinkedHashMap<>();
                cursor.put(path.get(index), created);
                cursor = created;
                continue;
            }
            cursor = (Map<String, Object>) map;
        }
        cursor.put(path.get(path.size() - 1), value);
    }

    public ModuleNode structureNode(Session session, String templateId, String nodeId) {
        TemplateMeta meta = structure(session, templateId);
        if (meta == null) {
            return null;
        }
        for (ModuleNode node : meta.nodes()) {
            if (node.nodeId().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }

    public boolean saveStructureNode(Session session, String templateId, ModuleNode updated) {
        TemplateMeta meta = structure(session, templateId);
        if (meta == null || updated == null) {
            return false;
        }
        List<ModuleNode> nodes = new ArrayList<>();
        for (ModuleNode node : meta.nodes()) {
            nodes.add(node.nodeId().equals(updated.nodeId()) ? updated : node);
        }
        return saveStructure(session, new TemplateMeta(meta.id(), meta.templateRef(), meta.structureType(),
                meta.tags(), meta.sizeX(), meta.sizeY(), meta.sizeZ(), meta.floors(), meta.anchors(),
                meta.rotatable(), meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(),
                meta.sealBlock(), nodes, meta.mask(), meta.entities(), meta.selection(), meta.weight()));
    }

    public boolean deleteStructureNode(Session session, String templateId, String nodeId) {
        TemplateMeta meta = structure(session, templateId);
        if (meta == null) {
            return false;
        }
        List<ModuleNode> nodes = new ArrayList<>();
        for (ModuleNode node : meta.nodes()) {
            if (!node.nodeId().equals(nodeId)) {
                nodes.add(node);
            }
        }
        return saveStructure(session, new TemplateMeta(meta.id(), meta.templateRef(), meta.structureType(),
                meta.tags(), meta.sizeX(), meta.sizeY(), meta.sizeZ(), meta.floors(), meta.anchors(),
                meta.rotatable(), meta.mirrorable(), meta.intrusionPolicy(), meta.doorPolicy(),
                meta.sealBlock(), nodes, meta.mask(), meta.entities(), meta.selection(), meta.weight()));
    }

    public boolean deleteStructure(Session session, String templateId) {
        TemplateStore store = storeProvider.apply(dungeonOf(session));
        boolean yml = new java.io.File(store.root(), templateId + ".yml").delete();
        boolean nbt = new java.io.File(store.root(), templateId + ".nbt").delete();
        return yml || nbt;
    }

    private String dungeonOf(Session session) {
        return session.worldName != null && session.worldName.startsWith(EDIT_WORLD_PREFIX)
                ? session.worldName.substring(EDIT_WORLD_PREFIX.length())
                : "";
    }

    private int[] anchorPos(Session session, String anchorId) {
        if (anchorId == null || anchorId.isBlank()) {
            return new int[] {0, 0, 0};
        }
        for (DoorAnchor anchor : session.anchors) {
            if (anchor.id().equals(anchorId)) {
                return new int[] {anchor.x(), anchor.y(), anchor.z()};
            }
        }
        return new int[] {0, 0, 0};
    }

    public Result discard(Player player) {
        Session removed = sessions.remove(player.getUniqueId());
        if (removed == null) {
            return Result.fail("random.template.no_session", Map.of());
        }
        clearMarkersOf(removed);
        return Result.ok("random.template.discarded", Map.of());
    }
}
