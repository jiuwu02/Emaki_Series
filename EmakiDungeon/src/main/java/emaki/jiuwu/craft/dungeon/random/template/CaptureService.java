package emaki.jiuwu.craft.dungeon.random.template;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.dungeon.random.config.RandomConfig;
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
        public boolean mirrorable;
        public String intrusionPolicy = TemplateModel.INTRUSION_ALLOW;
        public String doorPolicy = TemplateModel.POLICY_KEEP;
        public String sealBlock = "";
        public final List<Integer> rotations = new ArrayList<>(List.of(0, 90, 180, 270));
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

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final java.util.function.Function<String, TemplateStore> storeProvider;

    public CaptureService(java.util.function.Function<String, TemplateStore> storeProvider) {
        this.storeProvider = storeProvider;
    }

    private volatile java.util.function.Consumer<Session> refreshHook;

    public void bindRefreshHook(java.util.function.Consumer<Session> refreshHook) {
        this.refreshHook = refreshHook;
    }

    void fireRefresh(UUID playerId) {
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
        if (sessions.containsKey(player.getUniqueId())) {
            return Result.fail("random.template.already_open", Map.of());
        }
        World world = player.getWorld();
        if (world == null || !world.getName().startsWith(EDIT_WORLD_PREFIX)) {
            return Result.fail("random.template.world_forbidden", Map.of());
        }
        Session session = new Session();
        session.worldName = world.getName();
        session.playerId = player.getUniqueId();
        sessions.put(player.getUniqueId(), session);
        return Result.ok("random.template.started", Map.of("world", world.getName()));
    }

    public Result pos(Player player, int which) {
        try {
            return posInner(player, which);
        } finally {
            fireRefresh(player.getUniqueId());
        }
    }

    Result posInner(Player player, int which) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return Result.fail("random.template.no_session", Map.of());
        }
        Block block = player.getTargetBlockExact(12);
        if (block == null) {
            return Result.fail("random.template.no_target", Map.of());
        }
        int[] point = {block.getX(), block.getY(), block.getZ()};
        if (which == 1) {
            session.pos1 = point;
        } else {
            session.pos2 = point;
        }
        return Result.ok("random.template.pos_set", Map.of("which", String.valueOf(which),
                "coord", point[0] + "," + point[1] + "," + point[2]));
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
                    if (!TemplateModel.TYPE_ROOM.equals(value) && !TemplateModel.TYPE_CORRIDOR.equals(value)) {
                        return Result.fail("random.template.meta_invalid", Map.of("key", key, "value", value));
                    }
                    session.structureType = value;
                }
                case "tags" -> {
                    session.tags.clear();
                    for (String tag : value.split(",")) {
                        if (!tag.isBlank()) {
                            session.tags.add(tag.trim());
                        }
                    }
                }
                case "floors" -> session.floors = Math.max(1, Integer.parseInt(value.trim()));
                case "mirrorable" -> session.mirrorable = Boolean.parseBoolean(value.trim());
                case "intrusion-policy" -> session.intrusionPolicy = TemplateModel.normalizeIntrusion(value);
                case "door-policy" -> session.doorPolicy = TemplateModel.normalizePolicy(value);
                case "seal-block" -> session.sealBlock = value.trim();
                case "rotations" -> {
                    session.rotations.clear();
                    for (String part : value.split(",")) {
                        if (!part.isBlank()) {
                            int rotation = Integer.parseInt(part.trim());
                            if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
                                return Result.fail("random.template.meta_invalid", Map.of("key", key, "value", value));
                            }
                            session.rotations.add(rotation);
                        }
                    }
                }
                default -> {
                    return Result.fail("random.template.meta_unknown", Map.of("key", key));
                }
            }
        } catch (NumberFormatException exception) {
            return Result.fail("random.template.meta_invalid", Map.of("key", key, "value", value));
        }
        return Result.ok("random.template.meta_set", Map.of("key", key, "value", value));
    }

    public Result anchorAdd(Player player, String id, String facing, int width, int height) {
        try {
            return anchorAddInner(player, id, facing, width, height);
        } finally {
            fireRefresh(player.getUniqueId());
        }
    }

    Result anchorAddInner(Player player, String id, String facing, int width, int height) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return Result.fail("random.template.no_session", Map.of());
        }
        if (session.pos1 == null && session.pos2 == null) {
            return Result.fail("random.template.pos_missing", Map.of());
        }
        Block block = player.getTargetBlockExact(12);
        if (block == null) {
            return Result.fail("random.template.no_target", Map.of());
        }
        if (width < 1 || width > 16 || height < 1 || height > 16) {
            return Result.fail("random.template.anchor_invalid", Map.of("id", id));
        }
        for (DoorAnchor anchor : session.anchors) {
            if (anchor.id().equals(id)) {
                return Result.fail("random.template.anchor_dup", Map.of("id", id));
            }
        }
        session.anchors.add(new DoorAnchor(id, block.getX(), block.getY(), block.getZ(),
                TemplateModel.normalizeFacing(facing), width, height, block.getY(),
                session.anchors.size() + 1, "", session.sealBlock));
        return Result.ok("random.template.anchor_added", Map.of("id", id,
                "coord", block.getX() + "," + block.getY() + "," + block.getZ()));
    }

    public Result anchorRemove(Player player, String id) {
        try {
            return anchorRemoveInner(player, id);
        } finally {
            fireRefresh(player.getUniqueId());
        }
    }

    Result anchorRemoveInner(Player player, String id) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return Result.fail("random.template.no_session", Map.of());
        }
        boolean removed = session.anchors.removeIf(anchor -> anchor.id().equals(id));
        return removed
                ? Result.ok("random.template.anchor_removed", Map.of("id", id))
                : Result.fail("random.template.anchor_unknown", Map.of("id", id));
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
            return Result.fail("random.template.node_forbidden", Map.of("module", module));
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
            Result anchorCheck = validateAnchor(anchor, minX, minY, minZ, maxX, maxY, maxZ, session);
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
        TemplateMeta meta = new TemplateMeta(normalizedId, normalizedId, session.structureType,
                List.copyOf(session.tags), sizeX, sizeY, sizeZ, session.floors,
                List.copyOf(session.anchors), List.copyOf(session.rotations), session.mirrorable,
                session.intrusionPolicy, session.doorPolicy, session.sealBlock,
                List.copyOf(session.nodes), mask, entities,
                List.of(minX, minY, minZ, maxX, maxY, maxZ));
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
        if (!TemplateModel.TYPE_ROOM.equals(normalizedType) && !TemplateModel.TYPE_CORRIDOR.equals(normalizedType)) {
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
        return saved.ok() ? Result.ok("random.region_created", saved.params()) : saved;
    }

    private String nextRegionId(String dungeonId, String prefix) {
        TemplateStore store = storeProvider.apply(dungeonId);
        int max = 0;
        for (String existing : store.ids()) {
            if (!existing.startsWith(prefix + "_")) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(existing.substring(prefix.length() + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return prefix + "_" + (max + 1);
    }

    private void resetForNewRegion(Session session) {
        session.pos1 = null;
        session.pos2 = null;
        session.pendingBlock = null;
        session.structureType = TemplateModel.TYPE_ROOM;
        session.tags.clear();
        session.floors = 1;
        session.mirrorable = false;
        session.intrusionPolicy = TemplateModel.INTRUSION_ALLOW;
        session.doorPolicy = TemplateModel.POLICY_KEEP;
        session.sealBlock = "";
        session.rotations.clear();
        session.rotations.addAll(List.of(0, 90, 180, 270));
        session.anchors.clear();
        session.nodes.clear();
    }

    private Result validateAnchor(DoorAnchor anchor, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ, Session session) {
        boolean onSurface = anchor.x() == minX || anchor.x() == maxX
                || anchor.y() == minY || anchor.y() == maxY
                || anchor.z() == minZ || anchor.z() == maxZ;
        if (!onSurface) {
            return Result.fail("random.template.anchor_not_surface", Map.of("id", anchor.id()));
        }
        int frontX = anchor.x() + TemplateModel.facingDx(anchor.facing());
        int frontZ = anchor.z() + TemplateModel.facingDz(anchor.facing());
        boolean pointsOut = frontX < minX || frontX > maxX || frontZ < minZ || frontZ > maxZ;
        if (!pointsOut) {
            return Result.fail("random.template.anchor_facing_in", Map.of("id", anchor.id()));
        }
        int pdx = TemplateModel.facingDx(anchor.facing()) == 0 ? 1 : 0;
        int pdz = TemplateModel.facingDz(anchor.facing()) == 0 ? 1 : 0;
        for (int k = 0; k < anchor.width(); k++) {
            for (int y = 0; y < anchor.height(); y++) {
                int x = anchor.x() + pdx * k;
                int z = anchor.z() + pdz * k;
                int yy = anchor.baseY() + y;
                if (x < minX || x > maxX || yy < minY || yy > maxY || z < minZ || z > maxZ) {
                    return Result.fail("random.template.anchor_section_out", Map.of("id", anchor.id()));
                }
            }
        }
        boolean seal = TemplateModel.POLICY_SEAL.equals(anchor.policyOrDefault(session.doorPolicy));
        if (seal && anchor.sealBlock().isBlank() && session.sealBlock.isBlank()) {
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
                int[] anchorPos = anchorPosOf(meta, node.anchorId());
                int bx = (int) Math.round(node.dx()) + anchorPos[0];
                int by = (int) Math.round(node.dy()) + anchorPos[1];
                int bz = (int) Math.round(node.dz()) + anchorPos[2];
                if (bx == block.getX() && by == block.getY() && bz == block.getZ()) {
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

    private int[] anchorPosOf(TemplateMeta meta, String anchorId) {
        if (anchorId == null || anchorId.isBlank()) {
            return new int[] {0, 0, 0};
        }
        DoorAnchor anchor = meta.anchor(anchorId);
        return anchor == null ? new int[] {0, 0, 0} : new int[] {anchor.x(), anchor.y(), anchor.z()};
    }

    public Result discard(Player player) {
        Session removed = sessions.remove(player.getUniqueId());
        if (removed != null) {
            emaki.jiuwu.craft.dungeon.editor.EditorModuleMarkers.removeAll(removed.markers);
        }
        return removed == null
                ? Result.fail("random.template.no_session", Map.of())
                : Result.ok("random.template.discarded", Map.of());
    }
}
