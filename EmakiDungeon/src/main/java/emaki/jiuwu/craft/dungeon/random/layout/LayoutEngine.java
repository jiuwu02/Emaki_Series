package emaki.jiuwu.craft.dungeon.random.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import emaki.jiuwu.craft.dungeon.random.config.RandomConfig;
import emaki.jiuwu.craft.dungeon.random.gen.Seeds;
import emaki.jiuwu.craft.dungeon.random.gen.Topology;
import emaki.jiuwu.craft.dungeon.random.gen.Topology.Edge;
import emaki.jiuwu.craft.dungeon.random.gen.Topology.Node;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.CorridorCell;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.CorridorRun;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.Layout;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.LinkRecord;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.RoomPlacement;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.UnconnectedAnchor;
import emaki.jiuwu.craft.dungeon.random.model.Mask;
import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;

public final class LayoutEngine {

    public record Result(Layout layout, String failure, String detail) {

        public Result(Layout layout, String failure) {
            this(layout, failure, "");
        }
    }

    private static final String FAIL_CONFLICT = "conflict";
    private static final String FAIL_DOOR_REJECT = "door-reject";

    public Result layout(long layoutSeed, long orientationSeed, long linkSeed,
            Topology topology, Map<Integer, String> assignment, Map<String, TemplateMeta> metas,
            RandomConfig config, int maxBacktracks, int baseY) {
        // 统计每次失败卡在哪种连接形式, 供失败后诊断使用
        Map<String, Integer> stuckByForm = new LinkedHashMap<>();
        for (int attempt = 0; attempt < maxBacktracks; attempt++) {
            Result result = attempt(layoutSeed, orientationSeed, linkSeed, attempt,
                    topology, assignment, metas, config, baseY);
            if (result.layout() != null || FAIL_DOOR_REJECT.equals(result.failure())) {
                return result;
            }
            if (!result.detail().isBlank()) {
                stuckByForm.merge(result.detail(), 1, Integer::sum);
            }
        }
        return new Result(null, FAIL_CONFLICT, summarize(stuckByForm));
    }

    /** 把 1000 次失败按连接形式聚合成一行诊断, 例如 "corridor=812, direct=188"。 */
    private static String summarize(Map<String, Integer> stuckByForm) {
        if (stuckByForm.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        stuckByForm.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .limit(3)
                .forEach(entry -> {
                    if (builder.length() > 0) {
                        builder.append(", ");
                    }
                    builder.append(entry.getKey()).append('=').append(entry.getValue());
                });
        return builder.toString();
    }

    private Result attempt(long layoutSeed, long orientationSeed, long linkSeed, int attemptIndex,
            Topology topology, Map<Integer, String> assignment, Map<String, TemplateMeta> metas,
            RandomConfig config, int baseY) {
        Random layoutRandom = new Random(Seeds.mix(layoutSeed, attemptIndex));
        Random orientRandom = new Random(Seeds.mix(orientationSeed, attemptIndex));
        Random linkRandom = new Random(Seeds.mix(linkSeed, attemptIndex));
        Map<Integer, RoomPlacement> placed = new LinkedHashMap<>();
        List<CorridorRun> corridors = new ArrayList<>();
        List<LinkRecord> links = new ArrayList<>();
        Set<String> usedAnchors = new HashSet<>();
        Set<Long> corridorCells = new HashSet<>();

        String rootType = assignment.get(topology.root());
        if (rootType == null) {
            return new Result(null, FAIL_CONFLICT);
        }
        TemplateMeta rootMeta = metas.get(rootType);
        if (rootMeta == null) {
            return new Result(null, FAIL_CONFLICT);
        }
        Transforms.Variant rootVariant = pickVariant(rootMeta, config, orientRandom);
        placed.put(topology.root(), new RoomPlacement(topology.root(), rootType, rootMeta,
                rootVariant, 0, baseY, 0));

        Map<Integer, List<Integer>> treeAdjacency = new HashMap<>();
        List<Edge> loopEdges = new ArrayList<>();
        for (Edge edge : topology.edges()) {
            if (edge.tree()) {
                treeAdjacency.computeIfAbsent(edge.a(), key -> new ArrayList<>()).add(edge.b());
                treeAdjacency.computeIfAbsent(edge.b(), key -> new ArrayList<>()).add(edge.a());
            } else {
                loopEdges.add(edge);
            }
        }
        List<int[]> order = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        visited.add(topology.root());
        queue.add(topology.root());
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int neighbor : treeAdjacency.getOrDefault(current, List.of())) {
                if (visited.add(neighbor)) {
                    order.add(new int[] {current, neighbor});
                    queue.add(neighbor);
                }
            }
        }
        for (int[] pair : order) {
            RoomPlacement parent = placed.get(pair[0]);
            String childType = assignment.get(pair[1]);
            TemplateMeta childMeta = childType == null ? null : metas.get(childType);
            if (parent == null || childMeta == null) {
                return new Result(null, FAIL_CONFLICT);
            }
            Result failure = placeChild(parent, pair[1], childType, childMeta,
                    placed, corridors, links, usedAnchors, corridorCells,
                    layoutRandom, orientRandom, linkRandom, config, topology.edges());
            if (failure != null) {
                return failure;
            }
        }
        for (Edge edge : loopEdges) {
            RoomPlacement from = placed.get(edge.a());
            RoomPlacement to = placed.get(edge.b());
            if (from == null || to == null) {
                continue;
            }
            connectExisting(from, to, placed, corridors, links, usedAnchors, corridorCells,
                    orientRandom, linkRandom, config);
        }
        List<UnconnectedAnchor> unconnected = new ArrayList<>();
        for (RoomPlacement room : placed.values()) {
            for (DoorAnchor anchor : room.meta().anchors()) {
                if (usedAnchors.contains(room.nodeId() + ":" + anchor.id())) {
                    continue;
                }
                String policy = anchor.policyOrDefault(room.meta().doorPolicy());
                int[] world = room.anchorWorld(anchor);
                String facing = room.anchorFacing(anchor);
                if (TemplateModel.POLICY_REJECT.equals(policy)) {
                    return new Result(null, FAIL_DOOR_REJECT);
                }
                if (TemplateModel.POLICY_KEEP.equals(policy)
                        && entryZoneBlocked(room, anchor, world, facing, placed, corridorCells)) {
                    return new Result(null, FAIL_CONFLICT);
                }
                unconnected.add(new UnconnectedAnchor(room.nodeId(), room.typeId(), anchor.id(),
                        policy, world, facing, anchor.width(), anchor.height()));
            }
        }
        return new Result(new Layout(List.copyOf(placed.values()), List.copyOf(corridors),
                List.copyOf(links), List.copyOf(unconnected), attemptIndex + 1), "");
    }

    private Result placeChild(RoomPlacement parent, int childNodeId, String childType, TemplateMeta childMeta,
            Map<Integer, RoomPlacement> placed, List<CorridorRun> corridors, List<LinkRecord> links,
            Set<String> usedAnchors, Set<Long> corridorCells,
            Random layoutRandom, Random orientRandom, Random linkRandom, RandomConfig config, List<Edge> edges) {
        List<DoorAnchor> parentAnchors = new ArrayList<>(parent.meta().anchors());
        Collections.shuffle(parentAnchors, orientRandom);
        String lastForm = "none";
        boolean anyFreeAnchor = false;
        for (DoorAnchor parentAnchor : parentAnchors) {
            if (usedAnchors.contains(parent.nodeId() + ":" + parentAnchor.id())) {
                continue;
            }
            anyFreeAnchor = true;
            String form = decideForm(linkRandom, config);
            lastForm = form;
            int[] parentWorld = parent.anchorWorld(parentAnchor);
            String parentFacing = parent.anchorFacing(parentAnchor);
            if ("direct".equals(form)) {
                LinkAttempt direct = tryDirect(parent, parentAnchor, parentWorld, parentFacing,
                        childNodeId, childType, childMeta, placed, corridors, corridorCells,
                        orientRandom, config);
                if (direct != null) {
                    placed.put(childNodeId, direct.placement());
                    links.add(new LinkRecord(edges.indexOf(findEdge(edges, parent.nodeId(), childNodeId)),
                            parent.nodeId(), childNodeId, parentAnchor.id(), direct.anchorId(),
                            "direct", 0));
                    usedAnchors.add(parent.nodeId() + ":" + parentAnchor.id());
                    usedAnchors.add(childNodeId + ":" + direct.anchorId());
                    return null;
                }
            } else {
                LinkAttempt corridor = tryCorridor(parent, parentAnchor, parentWorld, parentFacing,
                        childNodeId, childType, childMeta, placed, corridors, corridorCells,
                        orientRandom, linkRandom, config);
                if (corridor != null) {
                    placed.put(childNodeId, corridor.placement());
                    links.add(new LinkRecord(edges.indexOf(findEdge(edges, parent.nodeId(), childNodeId)),
                            parent.nodeId(), childNodeId, parentAnchor.id(), corridor.anchorId(),
                            "corridor", corridor.heightDiff()));
                    usedAnchors.add(parent.nodeId() + ":" + parentAnchor.id());
                    usedAnchors.add(childNodeId + ":" + corridor.anchorId());
                    return null;
                }
            }
        }
        // 无空闲锚点 = 门数不足; 有空闲但全部放不下 = 几何/朝向冲突
        return new Result(null, FAIL_CONFLICT, anyFreeAnchor ? lastForm : "anchors-exhausted");
    }

    private record LinkAttempt(RoomPlacement placement, String anchorId, int heightDiff) {
    }

    private LinkAttempt tryDirect(RoomPlacement parent, DoorAnchor parentAnchor,
            int[] parentWorld, String parentFacing,
            int childNodeId, String childType, TemplateMeta childMeta,
            Map<Integer, RoomPlacement> placed, List<CorridorRun> corridors, Set<Long> corridorCells,
            Random orientRandom, RandomConfig config) {
        String opposite = TemplateModel.oppositeFacing(parentFacing);
        int dx = TemplateModel.facingDx(parentFacing);
        int dz = TemplateModel.facingDz(parentFacing);
        List<Transforms.Variant> variants = legalVariants(childMeta, config);
        Collections.shuffle(variants, orientRandom);
        for (Transforms.Variant variant : variants) {
            List<DoorAnchor> childAnchors = new ArrayList<>(childMeta.anchors());
            Collections.shuffle(childAnchors, orientRandom);
            for (DoorAnchor childAnchor : childAnchors) {
                if (childAnchor.width() != parentAnchor.width() || childAnchor.height() != parentAnchor.height()) {
                    continue;
                }
                if (!opposite.equals(Transforms.transformFacing(variant, childAnchor.facing()))) {
                    continue;
                }
                if (childAnchor.y() != parentAnchor.y()) {
                    continue;
                }
                int[] childAnchorWorld = {parentWorld[0] + dx, parentWorld[1], parentWorld[2] + dz};
                int[] local = Transforms.transform(childMeta, variant, childAnchor.x(), childAnchor.y(), childAnchor.z());
                RoomPlacement candidate = new RoomPlacement(childNodeId, childType, childMeta, variant,
                        childAnchorWorld[0] - local[0], childAnchorWorld[1] - local[1], childAnchorWorld[2] - local[2]);
                if (conflicts(candidate, placed, corridorCells)) {
                    continue;
                }
                return new LinkAttempt(candidate, childAnchor.id(), 0);
            }
        }
        return null;
    }

    private LinkAttempt tryCorridor(RoomPlacement parent, DoorAnchor parentAnchor,
            int[] parentWorld, String parentFacing,
            int childNodeId, String childType, TemplateMeta childMeta,
            Map<Integer, RoomPlacement> placed, List<CorridorRun> corridors, Set<Long> corridorCells,
            Random orientRandom, Random linkRandom, RandomConfig config) {
        int width = config.corridor.width();
        int height = config.corridor.height();
        int dx = TemplateModel.facingDx(parentFacing);
        int dz = TemplateModel.facingDz(parentFacing);
        int turn = config.corridor.maxTurns() >= 1 && linkRandom.nextBoolean() ? 1 : 0;
        int perpendicularSign = linkRandom.nextBoolean() ? 1 : -1;
        int pdx = dx == 0 ? perpendicularSign : 0;
        int pdz = dz == 0 ? perpendicularSign : 0;
        int baseLength = config.corridor.lengthMin()
                + linkRandom.nextInt(Math.max(1, config.corridor.lengthMax() - config.corridor.lengthMin() + 1));
        int firstRun;
        int secondRun;
        if (turn == 0) {
            firstRun = baseLength;
            secondRun = 0;
        } else {
            firstRun = Math.max(1, baseLength / 2);
            secondRun = Math.max(1, baseLength - firstRun);
        }
        List<Transforms.Variant> variants = legalVariants(childMeta, config);
        Collections.shuffle(variants, orientRandom);
        for (Transforms.Variant variant : variants) {
            List<DoorAnchor> childAnchors = new ArrayList<>(childMeta.anchors());
            Collections.shuffle(childAnchors, orientRandom);
            for (DoorAnchor childAnchor : childAnchors) {
                if (childAnchor.width() != width || childAnchor.height() != height) {
                    continue;
                }
                int heightDiff = parentAnchor.y() - childAnchor.y();
                if (Math.abs(heightDiff) > config.corridor.maxHeightDiff()) {
                    continue;
                }
                int lastDx = turn == 0 ? dx : pdx;
                int lastDz = turn == 0 ? dz : pdz;
                String wanted = TemplateModel.oppositeFacing(facingOf(lastDx, lastDz));
                if (!wanted.equals(Transforms.transformFacing(variant, childAnchor.facing()))) {
                    continue;
                }
                int stair = Math.abs(heightDiff);
                List<int[]> path = new ArrayList<>();
                int[] current = {parentWorld[0] + dx, parentWorld[1] - 1, parentWorld[2] + dz};
                path.add(new int[] {current[0], current[1], current[2]});
                for (int i = 0; i < firstRun; i++) {
                    current = new int[] {current[0] + dx, current[1], current[2] + dz};
                    path.add(new int[] {current[0], current[1], current[2]});
                }
                for (int i = 0; i < secondRun; i++) {
                    current = new int[] {current[0] + pdx, current[1], current[2] + pdz};
                    path.add(new int[] {current[0], current[1], current[2]});
                }
                int stepY = heightDiff > 0 ? -1 : 1;
                for (int i = 0; i < stair; i++) {
                    current = new int[] {current[0] + lastDx, current[1] + stepY, current[2] + lastDz};
                    path.add(new int[] {current[0], current[1], current[2]});
                }
                int[] endFloor = path.get(path.size() - 1);
                int[] childAnchorWorld = {endFloor[0] + lastDx, endFloor[1] + 1, endFloor[2] + lastDz};
                int[] local = Transforms.transform(childMeta, variant, childAnchor.x(), childAnchor.y(), childAnchor.z());
                RoomPlacement candidate = new RoomPlacement(childNodeId, childType, childMeta, variant,
                        childAnchorWorld[0] - local[0], childAnchorWorld[1] - local[1], childAnchorWorld[2] - local[2]);
                if (conflicts(candidate, placed, corridorCells)) {
                    continue;
                }
                List<CorridorCell> runCells = new ArrayList<>();
                boolean corridorOk = true;
                Set<Long> runSet = new HashSet<>();
                for (int[] floor : path) {
                    for (int columnY = 0; columnY <= height; columnY++) {
                        long packed = TemplateModel.packCell(floor[0], floor[1] + columnY, floor[2]);
                        if (corridorCells.contains(packed)) {
                            corridorOk = false;
                            break;
                        }
                        if (blocksRoomAt(floor[0], floor[1] + columnY, floor[2], placed, candidate)) {
                            corridorOk = false;
                            break;
                        }
                        if (maskCellAt(candidate, floor[0], floor[1] + columnY, floor[2])) {
                            corridorOk = false;
                            break;
                        }
                        runSet.add(packed);
                    }
                    if (!corridorOk) {
                        break;
                    }
                    runCells.add(new CorridorCell(floor[0], floor[1], floor[2], true));
                }
                if (!corridorOk) {
                    continue;
                }
                corridorCells.addAll(runSet);
                corridors.add(new CorridorRun(corridors.size(), List.copyOf(runCells)));
                return new LinkAttempt(candidate, childAnchor.id(), heightDiff);
            }
        }
        return null;
    }

    private void connectExisting(RoomPlacement from, RoomPlacement to,
            Map<Integer, RoomPlacement> placed, List<CorridorRun> corridors, List<LinkRecord> links,
            Set<String> usedAnchors, Set<Long> corridorCells,
            Random orientRandom, Random linkRandom, RandomConfig config) {
        String form = decideForm(linkRandom, config);
        List<DoorAnchor> fromAnchors = new ArrayList<>(from.meta().anchors());
        Collections.shuffle(fromAnchors, orientRandom);
        for (DoorAnchor anchor : fromAnchors) {
            if (usedAnchors.contains(from.nodeId() + ":" + anchor.id())) {
                continue;
            }
            int[] world = from.anchorWorld(anchor);
            String facing = from.anchorFacing(anchor);
            List<DoorAnchor> toAnchors = new ArrayList<>(to.meta().anchors());
            Collections.shuffle(toAnchors, orientRandom);
            for (DoorAnchor other : toAnchors) {
                if (usedAnchors.contains(to.nodeId() + ":" + other.id())) {
                    continue;
                }
                int[] otherWorld = to.anchorWorld(other);
                String otherFacing = to.anchorFacing(other);
                if ("direct".equals(form)) {
                    if (anchor.width() != other.width() || anchor.height() != other.height()) {
                        continue;
                    }
                    if (!TemplateModel.oppositeFacing(facing).equals(otherFacing)) {
                        continue;
                    }
                    if (world[0] + TemplateModel.facingDx(facing) != otherWorld[0]
                            || world[2] + TemplateModel.facingDz(facing) != otherWorld[2]
                            || world[1] != otherWorld[1]) {
                        continue;
                    }
                    links.add(new LinkRecord(-1, from.nodeId(), to.nodeId(), anchor.id(),
                            other.id(), "direct", 0));
                    usedAnchors.add(from.nodeId() + ":" + anchor.id());
                    usedAnchors.add(to.nodeId() + ":" + other.id());
                    return;
                }
                if (anchor.width() != config.corridor.width() || anchor.height() != config.corridor.height()) {
                    continue;
                }
                CorridorRun run = tryFixedCorridor(world, facing, otherWorld, otherFacing,
                        placed, from, to, corridorCells, links.size(), config);
                if (run != null) {
                    corridors.add(run);
                    links.add(new LinkRecord(-1, from.nodeId(), to.nodeId(), anchor.id(),
                            other.id(), "corridor", world[1] - otherWorld[1]));
                    usedAnchors.add(from.nodeId() + ":" + anchor.id());
                    usedAnchors.add(to.nodeId() + ":" + other.id());
                    return;
                }
            }
        }
    }

    private CorridorRun tryFixedCorridor(int[] fromWorld, String fromFacing,
            int[] toWorld, String toFacing, Map<Integer, RoomPlacement> placed,
            RoomPlacement skipFrom, RoomPlacement skipTo, Set<Long> corridorCells,
            int edgeIndex, RandomConfig config) {
        int[] startFloor = {fromWorld[0] + TemplateModel.facingDx(fromFacing), fromWorld[1] - 1,
                fromWorld[2] + TemplateModel.facingDz(fromFacing)};
        int[] endFloor = {toWorld[0] + TemplateModel.facingDx(toFacing), toWorld[1] - 1,
                toWorld[2] + TemplateModel.facingDz(toFacing)};
        int heightDiff = startFloor[1] - endFloor[1];
        if (Math.abs(heightDiff) > config.corridor.maxHeightDiff()) {
            return null;
        }
        int stair = Math.abs(heightDiff);
        int fdx = TemplateModel.facingDx(fromFacing);
        int fdz = TemplateModel.facingDz(fromFacing);
        int ox = endFloor[0] - startFloor[0];
        int oz = endFloor[2] - startFloor[2];
        int along = ox * fdx + oz * fdz;
        int perpendicular = fdx == 0 ? ox : oz;
        List<int[]> path = new ArrayList<>();
        path.add(new int[] {startFloor[0], startFloor[1], startFloor[2]});
        int[] current = {startFloor[0], startFloor[1], startFloor[2]};
        int stepY = heightDiff > 0 ? -1 : 1;
        if (perpendicular == 0) {
            if (along < stair + config.corridor.lengthMin() || along > stair + config.corridor.lengthMax()) {
                return null;
            }
            for (int i = 0; i < along - stair; i++) {
                current = new int[] {current[0] + fdx, current[1], current[2] + fdz};
                path.add(new int[] {current[0], current[1], current[2]});
            }
            for (int i = 0; i < stair; i++) {
                current = new int[] {current[0] + fdx, current[1] + stepY, current[2] + fdz};
                path.add(new int[] {current[0], current[1], current[2]});
            }
        } else {
            if (config.corridor.maxTurns() < 1 || along < 1 || Math.abs(perpendicular) < stair + 1) {
                return null;
            }
            int pdx = fdx == 0 ? (perpendicular > 0 ? 1 : -1) : 0;
            int pdz = fdz == 0 ? (perpendicular > 0 ? 1 : -1) : 0;
            int second = Math.abs(perpendicular) - stair;
            if (along + second < config.corridor.lengthMin() || along + second > config.corridor.lengthMax()) {
                return null;
            }
            for (int i = 0; i < along; i++) {
                current = new int[] {current[0] + fdx, current[1], current[2] + fdz};
                path.add(new int[] {current[0], current[1], current[2]});
            }
            for (int i = 0; i < second; i++) {
                current = new int[] {current[0] + pdx, current[1], current[2] + pdz};
                path.add(new int[] {current[0], current[1], current[2]});
            }
            for (int i = 0; i < stair; i++) {
                current = new int[] {current[0] + pdx, current[1] + stepY, current[2] + pdz};
                path.add(new int[] {current[0], current[1], current[2]});
            }
        }
        int height = config.corridor.height();
        List<CorridorCell> runCells = new ArrayList<>();
        Set<Long> runSet = new HashSet<>();
        for (int[] floor : path) {
            for (int columnY = 0; columnY <= height; columnY++) {
                long packed = TemplateModel.packCell(floor[0], floor[1] + columnY, floor[2]);
                if (corridorCells.contains(packed)) {
                    return null;
                }
                if (blocksRoomExcept(floor[0], floor[1] + columnY, floor[2], placed, skipFrom, skipTo)) {
                    return null;
                }
                if (maskCellAt(skipFrom, floor[0], floor[1] + columnY, floor[2])
                        || maskCellAt(skipTo, floor[0], floor[1] + columnY, floor[2])) {
                    return null;
                }
                runSet.add(packed);
            }
            runCells.add(new CorridorCell(floor[0], floor[1], floor[2], true));
        }
        corridorCells.addAll(runSet);
        return new CorridorRun(edgeIndex, List.copyOf(runCells));
    }

    private boolean maskCellAt(RoomPlacement room, int x, int y, int z) {
        if (room == null || room.meta().mask() == null) {
            return false;
        }
        int[] local = Transforms.transformInverse(room.meta(), room.variant(),
                x - room.originX(), y - room.originY(), z - room.originZ());
        return room.meta().mask().has(local[0], local[1], local[2]);
    }

    private boolean blocksRoomExcept(int x, int y, int z, Map<Integer, RoomPlacement> placed,
            RoomPlacement first, RoomPlacement second) {
        for (RoomPlacement room : placed.values()) {
            if (room == first || room == second) {
                continue;
            }
            if (maskCellAt(room, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private boolean entryZoneBlocked(RoomPlacement room, DoorAnchor anchor, int[] world, String facing,
            Map<Integer, RoomPlacement> placed, Set<Long> corridorCells) {
        int pdx = TemplateModel.facingDx(facing) == 0 ? 1 : 0;
        int pdz = TemplateModel.facingDz(facing) == 0 ? 1 : 0;
        for (int k = 0; k < anchor.width(); k++) {
            for (int y = anchor.baseY(); y < anchor.baseY() + anchor.height(); y++) {
                int x = world[0] + TemplateModel.facingDx(facing) + pdx * k;
                int z = world[2] + TemplateModel.facingDz(facing) + pdz * k;
                long packed = TemplateModel.packCell(x, y, z);
                if (corridorCells.contains(packed)) {
                    return true;
                }
                if (blocksRoomAt(x, y, z, placed, null)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean blocksRoomAt(int x, int y, int z, Map<Integer, RoomPlacement> placed, RoomPlacement skip) {
        for (RoomPlacement room : placed.values()) {
            if (room == skip) {
                continue;
            }
            if (maskCellAt(room, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private boolean conflicts(RoomPlacement candidate, Map<Integer, RoomPlacement> placed, Set<Long> corridorCells) {
        for (RoomPlacement room : placed.values()) {
            if (candidate.overlapsReal(room)) {
                return true;
            }
            if (candidate.boxIntersects(room) && !candidate.sharesBoundary(room)
                    && (TemplateModel.INTRUSION_REJECT.equals(candidate.meta().intrusionPolicy())
                            || TemplateModel.INTRUSION_REJECT.equals(room.meta().intrusionPolicy()))) {
                return true;
            }
        }
        Mask mask = candidate.meta().mask();
        if (mask != null) {
            for (long[] entry : mask.cellsList()) {
                int[] world = Transforms.transform(candidate.meta(), candidate.variant(),
                        TemplateModel.unpackX(entry[0]),
                        TemplateModel.unpackY(entry[0]),
                        TemplateModel.unpackZ(entry[0]));
                long packed = TemplateModel.packCell(
                        candidate.originX() + world[0],
                        candidate.originY() + world[1],
                        candidate.originZ() + world[2]);
                if (corridorCells.contains(packed)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Edge findEdge(List<Edge> edges, int a, int b) {
        for (Edge edge : edges) {
            if ((edge.a() == a && edge.b() == b) || (edge.a() == b && edge.b() == a)) {
                return edge;
            }
        }
        return new Edge(a, b, true);
    }

    private String decideForm(Random linkRandom, RandomConfig config) {
        String mode = config.corridor.linkMode();
        if ("force".equals(mode)) {
            return "corridor";
        }
        if ("off".equals(mode)) {
            return "direct";
        }
        return linkRandom.nextBoolean() ? "corridor" : "direct";
    }

    private String facingOf(int dx, int dz) {
        if (dx > 0) {
            return TemplateModel.FACING_E;
        }
        if (dx < 0) {
            return TemplateModel.FACING_W;
        }
        return dz > 0 ? TemplateModel.FACING_S : TemplateModel.FACING_N;
    }

    private Transforms.Variant pickVariant(TemplateMeta meta, RandomConfig config, Random random) {
        List<Transforms.Variant> variants = legalVariants(meta, config);
        return variants.get(random.nextInt(variants.size()));
    }

    private List<Transforms.Variant> legalVariants(TemplateMeta meta, RandomConfig config) {
        List<Transforms.Variant> variants = new ArrayList<>();
        boolean mirrorAllowed = meta.mirrorable() && "x".equals(config.orientation.mirrorAxis());
        for (int rotations : rotationSteps(meta.rotatable(), config.orientation.rotatable())) {
            variants.add(new Transforms.Variant(rotations, false));
            if (mirrorAllowed) {
                variants.add(new Transforms.Variant(rotations, true));
            }
        }
        return variants;
    }

    /** 模板级与全局级均可旋转才是四向, 否则只保留原始朝向。返回度数。 */
    static List<Integer> rotationSteps(boolean templateRotatable, boolean globalRotatable) {
        if (templateRotatable && globalRotatable) {
            return List.of(0, 90, 180, 270);
        }
        return List.of(0);
    }
}
