package emaki.jiuwu.craft.dungeon.random.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.ModuleNode;

/**
 * 模板元数据。
 *
 * <p>坐标约定 (format 2 起):
 * <ul>
 *   <li>door-anchors 的 pos / base-y 为模板局部坐标, 原点 = 选区最小角</li>
 *   <li>module-nodes 的 dx/dy/dz 为相对所属门锚点局部坐标的偏移; anchor 为空时相对选区最小角</li>
 *   <li>selection 仍记录选区绝对坐标, 仅用于编辑世界内还原选框位置</li>
 * </ul>
 *
 * <p>format 1 的历史数据在读取时按 selection 偏移就地归一化为 format 2 语义。
 */
public record TemplateMeta(String id, String templateRef, String structureType, List<String> tags,
        int sizeX, int sizeY, int sizeZ, int floors, List<DoorAnchor> anchors,
        boolean rotatable, boolean mirrorable, String intrusionPolicy, String doorPolicy,
        String sealBlock, List<ModuleNode> nodes, Mask mask, List<Map<String, Object>> entities,
        List<Integer> selection, int weight) {

    public static final int FORMAT_LEGACY = 1;
    public static final int FORMAT_LOCAL = 2;

    public TemplateMeta {
        weight = Math.max(0, weight);
        selection = selection == null ? List.of() : List.copyOf(selection);
        tags = tags == null ? List.of() : List.copyOf(tags);
        anchors = anchors == null ? List.of() : List.copyOf(anchors);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        entities = entities == null ? List.of() : List.copyOf(entities);
        intrusionPolicy = TemplateModel.normalizeIntrusion(intrusionPolicy);
        doorPolicy = TemplateModel.normalizePolicy(doorPolicy);
    }

    public TemplateMeta withAnchors(List<DoorAnchor> nextAnchors) {
        return new TemplateMeta(id, templateRef, structureType, tags, sizeX, sizeY, sizeZ, floors,
                nextAnchors, rotatable, mirrorable, intrusionPolicy, doorPolicy, sealBlock, nodes,
                mask, entities, selection, weight);
    }

    public DoorAnchor anchor(String anchorId) {
        for (DoorAnchor anchor : anchors) {
            if (anchor.id().equals(anchorId)) {
                return anchor;
            }
        }
        return null;
    }

    public boolean hasSelection() {
        return selection.size() == 6;
    }

    public int[] selectionMin() {
        if (!hasSelection()) {
            return new int[] {0, 0, 0};
        }
        return new int[] {
                Math.min(selection.get(0), selection.get(3)),
                Math.min(selection.get(1), selection.get(4)),
                Math.min(selection.get(2), selection.get(5))};
    }

    /** 编辑世界内的锚点绝对坐标 (仅用于编辑期渲染与点选)。 */
    public int[] anchorAbsolute(DoorAnchor anchor) {
        int[] min = selectionMin();
        return new int[] {min[0] + anchor.x(), min[1] + anchor.y(), min[2] + anchor.z()};
    }

    /** 编辑世界内的节点绝对坐标 (仅用于编辑期渲染与点选)。 */
    public int[] nodeAbsolute(ModuleNode node) {
        DoorAnchor anchor = node.anchorId() == null || node.anchorId().isBlank()
                ? null : anchor(node.anchorId());
        int[] base = anchor == null ? selectionMin() : anchorAbsolute(anchor);
        return new int[] {
                base[0] + (int) Math.round(node.dx()),
                base[1] + (int) Math.round(node.dy()),
                base[2] + (int) Math.round(node.dz())};
    }

    public Map<String, Object> toMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT_LOCAL);
        root.put("id", id);
        root.put("template-ref", templateRef);
        root.put("structure-type", structureType);
        root.put("tags", new ArrayList<>(tags));
        root.put("size-x", sizeX);
        root.put("size-y", sizeY);
        root.put("size-z", sizeZ);
        root.put("floors", floors);
        root.put("weight", weight);
        List<Object> anchorMaps = new ArrayList<>();
        for (DoorAnchor anchor : anchors) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", anchor.id());
            Map<String, Object> pos = new LinkedHashMap<>();
            pos.put("x", anchor.x());
            pos.put("y", anchor.y());
            pos.put("z", anchor.z());
            entry.put("pos", pos);
            entry.put("facing", anchor.facing());
            entry.put("width", anchor.width());
            entry.put("height", anchor.height());
            entry.put("base-y", anchor.baseY());
            entry.put("floor", anchor.floor());
            if (anchor.policy() != null && !anchor.policy().isBlank()) {
                entry.put("policy", anchor.policy());
            }
            if (anchor.sealBlock() != null && !anchor.sealBlock().isBlank()) {
                entry.put("seal-block", anchor.sealBlock());
            }
            anchorMaps.add(entry);
        }
        root.put("door-anchors", anchorMaps);
        root.put("rotatable", rotatable);
        root.put("mirrorable", mirrorable);
        root.put("intrusion-policy", intrusionPolicy);
        root.put("door-policy", doorPolicy);
        if (sealBlock != null && !sealBlock().isBlank()) {
            root.put("seal-block", sealBlock);
        }
        List<Object> nodeMaps = new ArrayList<>();
        for (ModuleNode node : nodes) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("module", node.moduleType());
            entry.put("id", node.nodeId());
            entry.put("anchor", node.anchorId());
            entry.put("dx", node.dx());
            entry.put("dy", node.dy());
            entry.put("dz", node.dz());
            entry.put("yaw", node.yaw());
            entry.put("params", new LinkedHashMap<>(node.params()));
            nodeMaps.add(entry);
        }
        root.put("module-nodes", nodeMaps);
        root.put("mask", mask == null ? "" : mask.serialize());
        if (!entities.isEmpty()) {
            root.put("entities", new ArrayList<>(entities));
        }
        if (!selection.isEmpty()) {
            root.put("selection", new ArrayList<>(selection));
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    public static TemplateMeta fromMap(String id, Map<String, Object> root) {
        if (root == null) {
            return null;
        }
        List<String> tags = new ArrayList<>();
        Object rawTags = root.get("tags");
        if (rawTags instanceof List<?> list) {
            for (Object entry : list) {
                tags.add(String.valueOf(entry));
            }
        }
        int format = intValue(root, "format", FORMAT_LEGACY);
        List<Integer> selection = new ArrayList<>();
        Object rawSelection = root.get("selection");
        if (rawSelection instanceof List<?> list) {
            for (Object entry : list) {
                selection.add(intValue(Map.of("v", entry), "v", 0));
            }
        }
        int[] min = new int[] {0, 0, 0};
        if (selection.size() == 6) {
            min = new int[] {
                    Math.min(selection.get(0), selection.get(3)),
                    Math.min(selection.get(1), selection.get(4)),
                    Math.min(selection.get(2), selection.get(5))};
        }
        boolean legacy = format < FORMAT_LOCAL;
        List<DoorAnchor> anchors = new ArrayList<>();
        Object rawAnchors = root.get("door-anchors");
        if (rawAnchors instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> typed = (Map<String, Object>) map;
                Map<String, Object> pos = (Map<String, Object>) typed.get("pos");
                int px = intValue(pos, "x", 0);
                int py = intValue(pos, "y", 0);
                int pz = intValue(pos, "z", 0);
                int baseY = intValue(typed, "base-y", py);
                String anchorId = String.valueOf(typed.getOrDefault("id", "anchor"));
                if (legacy) {
                    px -= min[0];
                    pz -= min[2];
                    py -= min[1];
                    baseY -= min[1];
                }
                anchors.add(new DoorAnchor(anchorId, px, py, pz,
                        String.valueOf(typed.getOrDefault("facing", TemplateModel.FACING_N)),
                        intValue(typed, "width", 1),
                        intValue(typed, "height", 3),
                        baseY,
                        intValue(typed, "floor", 1),
                        typed.get("policy") == null ? "" : String.valueOf(typed.get("policy")),
                        typed.get("seal-block") == null ? "" : String.valueOf(typed.get("seal-block"))));
            }
        }
        boolean rotatable = root.get("rotatable") instanceof Boolean flag
                ? flag : readLegacyRotatable(root);
        List<ModuleNode> nodes = new ArrayList<>();
        Object rawNodes = root.get("module-nodes");
        if (rawNodes instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> typed = (Map<String, Object>) map;
                Map<String, Object> params = typed.get("params") instanceof Map<?, ?> p
                        ? (Map<String, Object>) p
                        : Map.of();
                String anchorId = String.valueOf(typed.getOrDefault("anchor", ""));
                double dx = doubleValue(typed, "dx", 0.0D);
                double dy = doubleValue(typed, "dy", 0.0D);
                double dz = doubleValue(typed, "dz", 0.0D);
                if (legacy) {
                    if (anchorId.isBlank()) {
                        dx -= min[0];
                        dy -= min[1];
                        dz -= min[2];
                    }
                }
                nodes.add(new ModuleNode(
                        String.valueOf(typed.getOrDefault("module", "")),
                        String.valueOf(typed.getOrDefault("id", "")),
                        anchorId,
                        dx, dy, dz,
                        doubleValue(typed, "yaw", 0.0D),
                        params));
            }
        }
        List<Map<String, Object>> entities = new ArrayList<>();
        Object rawEntities = root.get("entities");
        if (rawEntities instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    entities.add((Map<String, Object>) map);
                }
            }
        }
        Mask mask = Mask.deserialize(String.valueOf(root.getOrDefault("mask", "")));
        return new TemplateMeta(
                id,
                String.valueOf(root.getOrDefault("template-ref", id)),
                String.valueOf(root.getOrDefault("structure-type", TemplateModel.TYPE_ROOM)),
                tags,
                intValue(root, "size-x", 1),
                intValue(root, "size-y", 1),
                intValue(root, "size-z", 1),
                Math.max(1, intValue(root, "floors", 1)),
                anchors,
                rotatable,
                root.get("mirrorable") instanceof Boolean mirrorFlag && mirrorFlag,
                String.valueOf(root.getOrDefault("intrusion-policy", TemplateModel.INTRUSION_ALLOW)),
                String.valueOf(root.getOrDefault("door-policy", TemplateModel.POLICY_SEAL)),
                String.valueOf(root.getOrDefault("seal-block", "")),
                nodes,
                mask,
                entities,
                selection,
                Math.max(0, intValue(root, "weight", 1)));
    }

    private static boolean readLegacyRotatable(Map<String, Object> root) {
        Object raw = root.get("rotations");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return true;
        }
        int turns = 0;
        for (Object entry : list) {
            if (entry instanceof Number number && number.intValue() != 0) {
                turns++;
            }
        }
        return turns > 0;
    }

    private static int intValue(Map<String, Object> map, String key, int fallback) {
        if (map == null) {
            return fallback;
        }
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double doubleValue(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
