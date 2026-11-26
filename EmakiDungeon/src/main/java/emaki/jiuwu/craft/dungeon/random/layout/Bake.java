package emaki.jiuwu.craft.dungeon.random.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.dungeon.random.layout.Placement.Layout;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.LinkRecord;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.RoomPlacement;
import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.ModuleNode;

public final class Bake {

    public record Result(Map<String, Object> flowRoot, int[] spawnPoint, List<String> warnings) {
    }

    public static Result bake(Layout layout, Map<String, TemplateMeta> metas) {
        Map<String, Object> root = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        Map<Integer, RoomPlacement> byNode = new LinkedHashMap<>();
        for (RoomPlacement room : layout.rooms()) {
            byNode.put(room.nodeId(), room);
        }
        RoomPlacement start = byNode.get(layout.rooms().get(0).nodeId());
        Map<String, Object> entryNode = findEntryNode(layout.rooms());
        int[] spawnPoint = entryNode != null
                ? entrySpawnPoint(start, entryNode)
                : resolveSpawnPoint(start, layout);
        Map<String, Object> spawnNode = new LinkedHashMap<>();
        spawnNode.put("id", "entry");
        spawnNode.put("enabled", true);
        spawnNode.put("location", pointMap(spawnPoint[0], spawnPoint[1], spawnPoint[2], spawnPoint[3]));
        root.put("spawn_point", spawnNode);
        Map<String, Object> roomsSection = new LinkedHashMap<>();
        int order = 1;
        for (RoomPlacement room : layout.rooms()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", "room_" + room.nodeId());
            entry.put("order", order++);
            entry.put("region", regionMap(
                    room.originX(), room.originY(), room.originZ(),
                    room.originX() + Transforms.footprintX(room.meta(), room.variant()) - 1,
                    room.originY() + room.meta().sizeY() - 1,
                    room.originZ() + Transforms.footprintZ(room.meta(), room.variant()) - 1));
            if (!room.meta().anchors().isEmpty()) {
                int[] arrival = room.anchorWorld(room.meta().anchors().get(0));
                entry.put("arrival", pointMap(arrival[0], arrival[1], arrival[2], 0));
            }
            roomsSection.put("room_" + room.nodeId(), entry);
        }
        root.put("room_zones", roomsSection);
        Map<String, Integer> sectionCounter = new LinkedHashMap<>();
        Map<String, Map<String, Object>> sections = new LinkedHashMap<>();
        for (RoomPlacement room : layout.rooms()) {
            for (ModuleNode node : room.meta().nodes()) {
                String section = node.moduleType();
                if (section == null || section.isBlank()) {
                    continue;
                }
                DoorAnchor anchor = node.anchorId().isBlank() ? null : room.meta().anchor(node.anchorId());
                int[] anchorWorld = anchor == null
                        ? new int[] {room.originX(), room.originY(), room.originZ()}
                        : room.anchorWorld(anchor);
                Map<String, Object> body = bakeParams(node.params(), anchorWorld, room);
                String id = node.nodeId() == null || node.nodeId().isBlank()
                        ? section + "_" + sectionCounter.merge(section, 1, Integer::sum)
                        : node.nodeId();
                body.put("id", id);
                body.putIfAbsent("enabled", true);
                body.putIfAbsent("location", pointMap(anchorWorld[0], anchorWorld[1], anchorWorld[2], (int) node.yaw()));
                Map<String, Object> sectionMap = sections.computeIfAbsent(section,
                        key -> new LinkedHashMap<>());
                if (sectionMap.containsKey(id)) {
                    warnings.add("节点 id 冲突已改写: " + section + "." + id);
                    id = id + "_" + sectionCounter.merge(section, 1, Integer::sum);
                    body.put("id", id);
                }
                sectionMap.put(id, body);
            }
        }
        sections.forEach(root::put);
        return new Result(root, spawnPoint, warnings);
    }

    /** 模板内的 spawn_point 节点 (采集面板里的 entry 模块), 作为副本真正的玩家入口。 */
    private static Map<String, Object> findEntryNode(List<RoomPlacement> rooms) {
        for (RoomPlacement room : rooms) {
            for (ModuleNode node : room.meta().nodes()) {
                if (TemplateModel.TYPE_ENTRY.equals(node.moduleType())) {
                    return Map.of("room", room, "node", node);
                }
            }
        }
        return null;
    }

    /** entry 节点: 模板局部坐标 = 所属锚点局部坐标 + 节点偏移, 再按房间变体变换到世界。 */
    @SuppressWarnings("unchecked")
    private static int[] entrySpawnPoint(RoomPlacement fallback, Map<String, Object> entry) {
        RoomPlacement room = (RoomPlacement) entry.get("room");
        ModuleNode node = (ModuleNode) entry.get("node");
        if (room == null || node == null) {
            return new int[] {fallback.originX(), fallback.originY(), fallback.originZ(), 0};
        }
        DoorAnchor anchor = node.anchorId().isBlank() ? null : room.meta().anchor(node.anchorId());
        int localX = (anchor == null ? 0 : anchor.x()) + (int) Math.round(node.dx());
        int localY = (anchor == null ? 0 : anchor.y()) + (int) Math.round(node.dy());
        int localZ = (anchor == null ? 0 : anchor.z()) + (int) Math.round(node.dz());
        int[] world = Transforms.transform(room.meta(), room.variant(), localX, localY, localZ);
        String facing = anchor == null ? "" : room.anchorFacing(anchor);
        return new int[] {room.originX() + world[0], room.originY() + world[1], room.originZ() + world[2],
                yawOf(facing)};
    }

    private static int[] resolveSpawnPoint(RoomPlacement start, Layout layout) {
        for (LinkRecord link : layout.links()) {
            if (link.parentNodeId() == start.nodeId()) {
                DoorAnchor anchor = start.meta().anchor(link.parentAnchorId());
                if (anchor != null) {
                    int[] world = start.anchorWorld(anchor);
                    return new int[] {world[0], world[1], world[2], yawOf(start.anchorFacing(anchor))};
                }
            }
        }
        if (!start.meta().anchors().isEmpty()) {
            DoorAnchor anchor = start.meta().anchors().get(0);
            int[] world = start.anchorWorld(anchor);
            return new int[] {world[0], world[1], world[2], yawOf(start.anchorFacing(anchor))};
        }
        return new int[] {start.originX(), start.originY(), start.originZ(), 0};
    }

    private static int yawOf(String facing) {
        return switch (TemplateModel.normalizeFacing(facing)) {
            case TemplateModel.FACING_E -> 90;
            case TemplateModel.FACING_S -> 180;
            case TemplateModel.FACING_W -> -90;
            default -> 0;
        };
    }

    private static Map<String, Object> bakeParams(Map<?, ?> params,
            int[] anchorWorld, RoomPlacement room) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : params.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                if (map.containsKey("dx") || map.containsKey("min")) {
                    result.put(key, bakeCoordinateNode(map, anchorWorld, room));
                } else {
                    result.put(key, bakeParams(map, anchorWorld, room));
                }
            } else if (value instanceof List<?> list) {
                List<Object> baked = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        if (map.containsKey("dx") || map.containsKey("min")) {
                            baked.add(bakeCoordinateNode(map, anchorWorld, room));
                        } else {
                            baked.add(bakeParams(map, anchorWorld, room));
                        }
                    } else {
                        baked.add(item);
                    }
                }
                result.put(key, baked);
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private static Map<String, Object> bakeCoordinateNode(Map<?, ?> node,
            int[] anchorWorld, RoomPlacement room) {
        if (node.containsKey("min")) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : new String[] {"min", "max"}) {
                if (node.get(key) instanceof Map<?, ?> corner) {
                    result.put(key, bakeCoordinateNode(corner, anchorWorld, room));
                }
            }
            return result;
        }
        double dx = doubleOf(node.get("dx"));
        double dy = doubleOf(node.get("dy"));
        double dz = doubleOf(node.get("dz"));
        double yaw = doubleOf(node.get("yaw"));
        double worldX = anchorWorld[0] + offsetValue(room.variant(), (int) Math.round(dx), (int) Math.round(dz), true);
        double worldZ = anchorWorld[2] + offsetValue(room.variant(), (int) Math.round(dx), (int) Math.round(dz), false);
        double worldY = anchorWorld[1] + dy;
        double worldYaw = yaw + room.variant().rotations();
        if (room.variant().mirror()) {
            worldYaw = -worldYaw;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", worldX);
        result.put("y", worldY);
        result.put("z", worldZ);
        if (node.containsKey("yaw") || node.containsKey("pitch")) {
            result.put("yaw", worldYaw);
            result.put("pitch", doubleOf(node.get("pitch")));
        }
        return result;
    }

    private static int offsetValue(Transforms.Variant variant, int dx, int dz, boolean xAxis) {
        int mirrorX = variant.mirror() ? -dx : dx;
        int[] rotated = switch (Math.floorMod(Math.floorDiv(variant.rotations(), 90), 4)) {
            case 1 -> new int[] {-dz, mirrorX};
            case 2 -> new int[] {-mirrorX, -dz};
            case 3 -> new int[] {dz, -mirrorX};
            default -> new int[] {mirrorX, dz};
        };
        return xAxis ? rotated[0] : rotated[1];
    }

    private static double doubleOf(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private static Map<String, Object> pointMap(double x, double y, double z, double yaw) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("x", x);
        point.put("y", y);
        point.put("z", z);
        point.put("yaw", yaw);
        point.put("pitch", 0.0D);
        return point;
    }

    private static Map<String, Object> regionMap(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Map<String, Object> min = new LinkedHashMap<>();
        min.put("x", minX);
        min.put("y", minY);
        min.put("z", minZ);
        Map<String, Object> max = new LinkedHashMap<>();
        max.put("x", maxX);
        max.put("y", maxY);
        max.put("z", maxZ);
        Map<String, Object> region = new LinkedHashMap<>();
        region.put("min", min);
        region.put("max", max);
        return region;
    }
}
