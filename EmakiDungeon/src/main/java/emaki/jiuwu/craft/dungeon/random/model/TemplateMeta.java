package emaki.jiuwu.craft.dungeon.random.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.ModuleNode;

public record TemplateMeta(String id, String templateRef, String structureType, List<String> tags,
        int sizeX, int sizeY, int sizeZ, int floors, List<DoorAnchor> anchors,
        List<Integer> rotations, boolean mirrorable, String intrusionPolicy, String doorPolicy,
        String sealBlock, List<ModuleNode> nodes, Mask mask, List<Map<String, Object>> entities,
        List<Integer> selection, int weight) {

    public TemplateMeta {
        weight = Math.max(0, weight);
        selection = selection == null ? List.of() : List.copyOf(selection);
        tags = tags == null ? List.of() : List.copyOf(tags);
        anchors = anchors == null ? List.of() : List.copyOf(anchors);
        rotations = rotations == null ? List.of(0, 90, 180, 270) : List.copyOf(rotations);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        entities = entities == null ? List.of() : List.copyOf(entities);
        intrusionPolicy = TemplateModel.normalizeIntrusion(intrusionPolicy);
        doorPolicy = TemplateModel.normalizePolicy(doorPolicy);
    }

    public DoorAnchor anchor(String anchorId) {
        for (DoorAnchor anchor : anchors) {
            if (anchor.id().equals(anchorId)) {
                return anchor;
            }
        }
        return null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", 1);
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
        root.put("rotations", new ArrayList<>(rotations));
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
                anchors.add(new DoorAnchor(
                        String.valueOf(typed.getOrDefault("id", "anchor")),
                        px, py, pz,
                        String.valueOf(typed.getOrDefault("facing", TemplateModel.FACING_N)),
                        intValue(typed, "width", 1),
                        intValue(typed, "height", 3),
                        intValue(typed, "base-y", py),
                        intValue(typed, "floor", 1),
                        typed.get("policy") == null ? "" : String.valueOf(typed.get("policy")),
                        typed.get("seal-block") == null ? "" : String.valueOf(typed.get("seal-block"))));
            }
        }
        List<Integer> rotations = new ArrayList<>();
        Object rawRotations = root.get("rotations");
        if (rawRotations instanceof List<?> list && !list.isEmpty()) {
            for (Object entry : list) {
                rotations.add(intValue(Map.of("v", entry), "v", 0));
            }
        } else {
            rotations = new ArrayList<>(List.of(0, 90, 180, 270));
        }
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
                nodes.add(new ModuleNode(
                        String.valueOf(typed.getOrDefault("module", "")),
                        String.valueOf(typed.getOrDefault("id", "")),
                        String.valueOf(typed.getOrDefault("anchor", "")),
                        doubleValue(typed, "dx", 0.0D),
                        doubleValue(typed, "dy", 0.0D),
                        doubleValue(typed, "dz", 0.0D),
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
        List<Integer> selection = new ArrayList<>();
        Object rawSelection = root.get("selection");
        if (rawSelection instanceof List<?> list) {
            for (Object entry : list) {
                selection.add(intValue(Map.of("v", entry), "v", 0));
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
                rotations,
                root.get("mirrorable") instanceof Boolean flag && flag,
                String.valueOf(root.getOrDefault("intrusion-policy", TemplateModel.INTRUSION_ALLOW)),
                String.valueOf(root.getOrDefault("door-policy", TemplateModel.POLICY_SEAL)),
                String.valueOf(root.getOrDefault("seal-block", "")),
                nodes,
                mask,
                entities,
                selection,
                Math.max(0, intValue(root, "weight", 1)));
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
