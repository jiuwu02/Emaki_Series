package emaki.jiuwu.craft.dungeon.random.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import emaki.jiuwu.craft.dungeon.random.build.CorridorAssembler;
import emaki.jiuwu.craft.dungeon.random.config.RandomConfig;
import emaki.jiuwu.craft.dungeon.random.gen.Assigner;
import emaki.jiuwu.craft.dungeon.random.gen.Seeds;
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

public final class ConstraintLayoutEngine {

    public record Result(Layout layout, String failure, int searchStates) {
        public Result {
            if (failure == null || failure.isBlank()) {
                failure = "search-exhausted";
            }
        }
    }

    public Result generate(long seed, int roomCount, Map<String, TemplateMeta> metas,
            List<Assigner.TemplateOption> options, List<Assigner.MandatoryRule> rules,
            RandomConfig config, int baseY) {
        Search search = new Search(seed, Math.max(1, roomCount), metas, options, rules, config, baseY);
        return search.run();
    }

    private record Expansion(RoomPlacement parent, DoorAnchor parentAnchor,
            RoomPlacement child, DoorAnchor childAnchor, CorridorRun corridor,
            String form, int heightDiff, int floor, boolean loop, double weight) {
    }

    private record Port(RoomPlacement room, DoorAnchor anchor, List<Expansion> expansions) {
    }

    private record Undo(RoomPlacement addedRoom, LinkRecord link, CorridorRun corridor,
            String firstPort, String secondPort, boolean addedFirstPort, boolean addedSecondPort,
            String typeId, int oldCount,
            List<Long> roomCells, List<Long> corridorCells, Integer oldFloor) {
    }

    private static final class Search {
        private final Random random;
        private final int targetRooms;
        private final Map<String, TemplateMeta> metas;
        private final List<Assigner.TemplateOption> options;
        private final List<Assigner.MandatoryRule> rules;
        private final RandomConfig config;
        private final int baseY;
        private final List<RoomPlacement> rooms = new ArrayList<>();
        private final Map<Integer, RoomPlacement> byId = new LinkedHashMap<>();
        private final List<LinkRecord> links = new ArrayList<>();
        private final List<CorridorRun> corridors = new ArrayList<>();
        private final Map<Long, Integer> occupied = new HashMap<>();
        private final Set<Long> corridorCells = new HashSet<>();
        private final Set<String> usedPorts = new HashSet<>();
        private final Set<String> linkedPairs = new HashSet<>();
        private final Map<String, Integer> counts = new HashMap<>();
        private final Map<Integer, Integer> floors = new LinkedHashMap<>();
        /** 走廊模板候选 (id 与权重), 每段走廊按权重抽一个; 为空时走纯程序化地板。 */
        private final List<Assigner.TemplateOption> corridorOptions = new ArrayList<>();
        private int searchStates;
        private int evaluations;
        private String failure = "";
        private boolean terminal;
        private final long deadline;

        private Search(long seed, int targetRooms, Map<String, TemplateMeta> metas,
                List<Assigner.TemplateOption> options, List<Assigner.MandatoryRule> rules,
                RandomConfig config, int baseY) {
            this.random = new Random(Seeds.mix(seed, 0x4C41594F5554L));
            this.targetRooms = targetRooms;
            this.metas = metas;
            this.options = options;
            this.rules = rules;
            this.config = config;
            this.baseY = baseY;
            this.deadline = System.nanoTime() + Math.max(1, config.limits.computeBudgetMs()) * 1_000_000L;
            for (Assigner.TemplateOption option : options) {
                counts.put(option.typeId(), 0);
            }
            collectCorridorOptions();
        }

        /** 收集合格直走廊模板: 显式 template-ref 优先, 否则按 corridor-types 或模板自带 weight。 */
        private void collectCorridorOptions() {
            String configured = config.corridor.templateRef();
            if (configured != null && !configured.isBlank()) {
                TemplateMeta meta = metas.get(configured);
                if (meta != null && TemplateModel.TYPE_CORRIDOR.equals(meta.structureType())) {
                    corridorOptions.add(new Assigner.TemplateOption(configured, meta.tags(),
                            Math.max(1, meta.weight()), 0, 0, meta.anchors().size()));
                }
                return;
            }
            for (Map.Entry<String, TemplateMeta> entry : metas.entrySet()) {
                TemplateMeta meta = entry.getValue();
                if (!TemplateModel.TYPE_CORRIDOR.equals(meta.structureType())) {
                    continue;
                }
                RandomConfig.CorridorType registered = config.corridorTypes.get(entry.getKey());
                int weight = registered == null || registered.weight() == null
                        ? meta.weight() : registered.weight();
                if (weight <= 0) {
                    continue;
                }
                corridorOptions.add(new Assigner.TemplateOption(entry.getKey(), meta.tags(),
                        weight, 0, 0, meta.anchors().size()));
            }
            corridorOptions.sort((left, right) -> left.typeId().compareTo(right.typeId()));
        }

        /** 按权重抽一个走廊模板 id; 无候选返回 null。 */
        private String pickCorridorTemplate() {
            if (corridorOptions.isEmpty()) {
                return null;
            }
            int total = 0;
            for (Assigner.TemplateOption option : corridorOptions) {
                total += Math.max(1, option.weight());
            }
            int roll = random.nextInt(total);
            for (Assigner.TemplateOption option : corridorOptions) {
                roll -= Math.max(1, option.weight());
                if (roll < 0) {
                    return option.typeId();
                }
            }
            return corridorOptions.get(corridorOptions.size() - 1).typeId();
        }

        private Result run() {
            List<RoomPlacement> roots = roots();
            if (roots.isEmpty()) {
                return new Result(null, "no-root", 0);
            }
            roots = shuffledRoots(roots);
            for (RoomPlacement root : roots) {
                rooms.add(root);
                byId.put(root.nodeId(), root);
                floors.put(root.nodeId(), 1);
                counts.merge(root.typeId(), 1, Integer::sum);
                occupy(root);
                if (search()) {
                    return new Result(finish(), "", searchStates);
                }
                clear();
                if (terminal || "search-exhausted".equals(failure) || "budget".equals(failure)) {
                    break;
                }
            }
            return new Result(null, failure.isBlank() ? "search-exhausted" : failure, searchStates);
        }

        private void clear() {
            rooms.clear();
            byId.clear();
            links.clear();
            corridors.clear();
            occupied.clear();
            corridorCells.clear();
            usedPorts.clear();
            linkedPairs.clear();
            floors.clear();
            counts.replaceAll((key, value) -> 0);
        }

        private boolean search() {
            if (roomCount() == targetRooms) {
                return validFinal();
            }
            if (roomCount() > targetRooms || nodesExceeded()) {
                return false;
            }
            if (terminal) {
                return false;
            }
            if (remainingRoomNeed() > remainingRoomSlots()) {
                failure = "mandatory-capacity";
                return false;
            }
            Port port = choosePort();
            if (port == null) {
                if (!terminal) {
                    failure = "no-compatible-anchor";
                }
                return false;
            }
            List<Expansion> candidates = port.expansions();
            for (Expansion expansion : candidates) {
                if (searchStates >= config.spatial.maxSearchStates()) {
                    failure = "search-exhausted";
                    terminal = true;
                    return false;
                }
                searchStates++;
                if ((searchStates & 127) == 0 && System.nanoTime() > deadline) {
                    failure = "budget";
                    return false;
                }
                Undo undo = apply(expansion);
                if (search()) {
                    return true;
                }
                undo(undo);
                if (terminal) {
                    return false;
                }
            }
            return false;
        }

        private Port choosePort() {
            if (terminal) {
                return null;
            }
            Port best = null;
            for (RoomPlacement room : rooms) {
                for (DoorAnchor anchor : room.meta().anchors()) {
                    if (usedPorts.contains(portKey(room.nodeId(), anchor.id()))) {
                        continue;
                    }
                    List<Expansion> candidates = constrained(expansions(room, anchor));
                    if (candidates.isEmpty()) {
                        continue;
                    }
                    Port candidate = new Port(room, anchor, candidates);
                    if (best == null || candidates.size() < best.expansions().size()) {
                        best = candidate;
                    }
                }
            }
            return best;
        }

        private List<Expansion> constrained(List<Expansion> input) {
            List<Expansion> result = new ArrayList<>();
            int remaining = targetRooms - roomCount();
            Set<String> forcedTypes = forcedTypes(remaining);
            int missingFloors = missingFloors();
            boolean onlyLoops = false;
            for (Expansion expansion : input) {
                if (remaining == 0 && mandatoryValid() && expansion.loop()) {
                    onlyLoops = true;
                    break;
                }
            }
            for (Expansion expansion : input) {
                if (remaining == 0 && mandatoryValid() && !expansion.loop()) {
                    continue;
                }
                if (remaining <= 0 && !expansion.loop()) {
                    continue;
                }
                if (!expansion.loop() && !forcedTypes.isEmpty()
                        && !forcedTypes.contains(expansion.child().typeId())) {
                    continue;
                }
                if (!expansion.loop() && "floors".equals(config.spatial.mode())
                        && missingFloors > 0 && remaining <= missingFloors
                        && expansion.floor() == floors.get(expansion.parent().nodeId())) {
                    continue;
                }
                result.add(expansion);
            }
            if (onlyLoops) {
                return weighted(result);
            }
            return result.isEmpty() ? List.of() : weighted(result);
        }

        private Set<String> forcedTypes(int remaining) {
            if (remaining <= 0) {
                return Set.of();
            }
            Set<String> forced = new LinkedHashSet<>();
            for (Assigner.TemplateOption option : options) {
                if (!isRoom(option.typeId())) {
                    continue;
                }
                int count = counts.getOrDefault(option.typeId(), 0);
                if (option.minCount() - count >= remaining) {
                    forced.add(option.typeId());
                }
            }
            for (Assigner.MandatoryRule rule : rules) {
                int count = mandatoryCount(rule);
                int need = Math.max(1, rule.minCount()) - count;
                if (need >= remaining) {
                    for (Assigner.TemplateOption option : options) {
                        if (isRoom(option.typeId()) && option.carries(rule.tag())) {
                            forced.add(option.typeId());
                        }
                    }
                }
            }
            return forced;
        }

        private int remainingRoomNeed() {
            int total = 0;
            for (Assigner.TemplateOption option : options) {
                if (isRoom(option.typeId())) {
                    total += Math.max(0, option.minCount() - counts.getOrDefault(option.typeId(), 0));
                }
            }
            for (Assigner.MandatoryRule rule : rules) {
                total += Math.max(0, Math.max(1, rule.minCount()) - mandatoryCount(rule));
            }
            return total;
        }

        private int remainingRoomSlots() {
            return targetRooms - roomCount();
        }

        private int floorTarget() {
            if (!"floors".equals(config.spatial.mode())) {
                return 1;
            }
            return verticalCapable() ? config.spatial.floorCount() : 1;
        }

        private boolean verticalCapable() {
            for (TemplateMeta meta : metas.values()) {
                for (DoorAnchor anchor : meta.anchors()) {
                    if (TemplateModel.isVertical(anchor.facing())) {
                        return true;
                    }
                }
            }
            return false;
        }

        private int pendingFloors() {
            if (!"floors".equals(config.spatial.mode())) {
                return 0;
            }
            int count = 0;
            for (int floor = 1; floor <= floorTarget(); floor++) {
                if (!floors.containsValue(floor)) {
                    count++;
                }
            }
            return count;
        }

        private int missingFloors() {
            if (!"floors".equals(config.spatial.mode())) {
                return 0;
            }
            return pendingFloors();
        }

        private List<RoomPlacement> roots() {
            List<RoomPlacement> result = new ArrayList<>();
            for (Assigner.TemplateOption option : options) {
                if (!isRoom(option.typeId()) || option.weight() <= 0 && option.minCount() <= 0) {
                    continue;
                }
                TemplateMeta meta = metas.get(option.typeId());
                if (meta == null || meta.anchors().isEmpty() && targetRooms > 1) {
                    continue;
                }
                if (!rootEligible(option)) {
                    continue;
                }
                for (Transforms.Variant variant : variants(meta)) {
                    result.add(new RoomPlacement(0, option.typeId(), meta, variant, 0, baseY, 0));
                }
            }
            return result;
        }

        private boolean rootEligible(Assigner.TemplateOption option) {
            for (Assigner.MandatoryRule rule : rules) {
                if ("root".equals(rule.placementRule()) && rule.minCount() > 0
                        && !option.carries(rule.tag())) {
                    return false;
                }
            }
            return true;
        }

        private List<Expansion> expansions(RoomPlacement parent, DoorAnchor parentAnchor) {
            if (++evaluations > config.spatial.maxSearchStates() * 4) {
                failure = "search-exhausted";
                terminal = true;
                return List.of();
            }
            List<Expansion> result = new ArrayList<>();
            int parentFloor = floors.getOrDefault(parent.nodeId(), 1);
            String facing = parent.anchorFacing(parentAnchor);
            int[] from = parent.anchorWorld(parentAnchor);
            for (Assigner.TemplateOption option : options) {
                if (option.maxCount() > 0 && counts.getOrDefault(option.typeId(), 0) >= option.maxCount()) {
                    continue;
                }
                TemplateMeta childMeta = metas.get(option.typeId());
                if (childMeta == null || childMeta.mask() == null) {
                    continue;
                }
                if (targetRooms - roomCount() <= 0) {
                    continue;
                }
                for (Transforms.Variant variant : variants(childMeta)) {
                    for (DoorAnchor childAnchor : childMeta.anchors()) {
                        if (!DoorCompatibility.from(parentAnchor).accepts(DoorCompatibility.from(childAnchor))) {
                            continue;
                        }
                        if (config.spatial.verticalChance() <= 0 && (TemplateModel.isVertical(facing)
                                || hasVerticalDeclaration(parentAnchor, childAnchor))) {
                            continue;
                        }
                        if (!"free".equals(config.spatial.mode())
                                && (TemplateModel.isVertical(facing) || hasVerticalDeclaration(parentAnchor, childAnchor))
                                && !floorAllowed(parentFloor, declaredFloorDelta(parentAnchor, childAnchor,
                                        TemplateModel.facingDy(facing)))) {
                            continue;
                        }
                        if (TemplateModel.isVertical(facing)) {
                            addVertical(result, parent, parentAnchor, from, facing, parentFloor,
                                    childMeta, variant, childAnchor, option.weight());
                        } else if (!TemplateModel.isVertical(childAnchor.facing())) {
                            addHorizontal(result, parent, parentAnchor, from, facing, parentFloor,
                                    childMeta, variant, childAnchor, option.weight());
                        }
                    }
                }
            }
            if (config.spatial.loopChance() > 0) {
                addLoops(result, parent, parentAnchor, from, facing);
            }
            return result;
        }

        private void addVertical(List<Expansion> result, RoomPlacement parent, DoorAnchor parentAnchor,
                int[] from, String facing, int parentFloor, TemplateMeta childMeta,
                Transforms.Variant variant, DoorAnchor childAnchor, int weight) {
            if (!TemplateModel.oppositeFacing(facing).equals(Transforms.transformFacing(variant, childAnchor.facing()))) {
                return;
            }
            int direction = TemplateModel.facingDy(facing);
            int declaredFloor = declaredFloorDelta(parentAnchor, childAnchor, direction);
            if (declaredFloor != 0 && Math.abs(declaredFloor) > 1 && declaredFloor > 0 != direction > 0) {
                return;
            }
            if (config.spatial.templateConnections()) {
                int delta = direction;
                int[] target = {from[0], from[1] + delta, from[2]};
                RoomPlacement child = placeAt(rooms.size(), childMeta, variant, childAnchor, target);
                if (child != null && validRoom(child, parent, true)) {
                    result.add(new Expansion(parent, parentAnchor, child, childAnchor, null,
                            "template", delta, floorFor(parentFloor, declaredFloor, child.originY()), false,
                            expansionWeight(weight, true)));
                }
            }
            if (config.spatial.proceduralConnections()) {
                int rise = config.spatial.floorSpacing();
                rise += Math.abs(valueOrZero(childAnchor.verticalDelta(), parentAnchor.verticalDelta()));
                if (rise > 0 && rise <= Math.max(config.corridor.maxHeightDiff(), config.spatial.floorSpacing() * 2)) {
                    String templateId = pickCorridorTemplate();
                    int length = randomLength();
                    int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] horizontal : directions) {
                        List<CorridorCell> path = new ArrayList<>();
                        int x = from[0];
                        int y = from[1] - 1;
                        int z = from[2];
                        for (int i = 0; i < rise + length; i++) {
                            x += horizontal[0];
                            z += horizontal[1];
                            if (i < rise) {
                                y += direction;
                            }
                            path.add(new CorridorCell(x, y, z, true));
                        }
                        int[] target = {x + horizontal[0], y + 1, z + horizontal[1]};
                        RoomPlacement child = placeAt(rooms.size(), childMeta, variant, childAnchor, target);
                        if (child == null || !validRoom(child, parent, false)
                                || !validPath(path, parent, child, parentAnchor.height())) {
                            continue;
                        }
                        int floor = floorFor(parentFloor, 0, child.originY());
                        result.add(new Expansion(parent, parentAnchor, child, childAnchor,
                                new CorridorRun(-1, List.copyOf(path),
                                        parentAnchor.width(), parentAnchor.height(), templateId),
                                "staircase",
                                target[1] - from[1], floor, false,
                                expansionWeight(weight, true)));
                    }
                }
            }
        }

        private void addHorizontal(List<Expansion> result, RoomPlacement parent, DoorAnchor parentAnchor,
                int[] from, String facing, int parentFloor, TemplateMeta childMeta,
                Transforms.Variant variant, DoorAnchor childAnchor, int weight) {
            if (!TemplateModel.oppositeFacing(facing).equals(Transforms.transformFacing(variant, childAnchor.facing()))) {
                return;
            }
            int[] directTarget = {from[0] + TemplateModel.facingDx(facing), from[1],
                    from[2] + TemplateModel.facingDz(facing)};
            RoomPlacement direct = placeAt(rooms.size(), childMeta, variant, childAnchor, directTarget);
            if (config.corridor.directAllowed()
                    && direct != null && parentAnchor.verticalDelta() == null && childAnchor.verticalDelta() == null
                    && parentAnchor.floorDelta() == null && childAnchor.floorDelta() == null
                    && validRoom(direct, parent, true)) {
                result.add(new Expansion(parent, parentAnchor, direct, childAnchor, null,
                        "direct", 0, floorFor(parentFloor, 0, direct.originY()), false,
                        expansionWeight(weight, false)));
            }
            if (!corridorAllowed(parentAnchor)) {
                return;
            }
            int heightDiff = chooseHeight(parentAnchor, childAnchor);
            if (parentAnchor.floorDelta() != null && parentAnchor.floorDelta() != 0
                    || childAnchor.floorDelta() != null && childAnchor.floorDelta() != 0) {
                return;
            }
            if (Math.abs(heightDiff) > config.corridor.maxHeightDiff()) {
                return;
            }
            int dx = TemplateModel.facingDx(facing);
            int dz = TemplateModel.facingDz(facing);
            int sign = random.nextBoolean() ? 1 : -1;
            int pdx = dx == 0 ? sign : 0;
            int pdz = dz == 0 ? sign : 0;
            String templateId = pickCorridorTemplate();
            int tile = shortestCorridorTile();
            int length = randomLength();
            // 拐弯必须把一段切成两个同样能整块平铺的子段, 否则拐点两侧都会剩半块模板,
            // 收尾门框被截断, 表现为走廊端口与房间门"左右差两格"。段长不够两个整块时
            // 直接走直线, 不做拐弯。
            boolean turn = config.corridor.maxTurns() > 0 && (tile <= 0 || length >= 2 * tile)
                    && random.nextBoolean();
            int prefix = turn ? roundUpToTile(length / 2, tile) : length;
            int suffix = turn ? length - prefix : 0;
            int stairs = Math.abs(heightDiff);
            int stepX = turn ? pdx : dx;
            int stepZ = turn ? pdz : dz;
            List<CorridorCell> path = new ArrayList<>();
            int x = from[0] + dx;
            int y = from[1] - 1;
            int z = from[2] + dz;
            path.add(new CorridorCell(x, y, z, true));
            for (int i = 1; i < prefix; i++) {
                x += dx;
                z += dz;
                path.add(new CorridorCell(x, y, z, true));
            }
            int stepY = Integer.signum(heightDiff);
            for (int i = 0; i < stairs; i++) {
                x += stepX;
                y += stepY;
                z += stepZ;
                path.add(new CorridorCell(x, y, z, true));
            }
            for (int i = 0; i < suffix; i++) {
                x += stepX;
                z += stepZ;
                path.add(new CorridorCell(x, y, z, true));
            }
            int[] target = {x + stepX, y + 1, z + stepZ};
            String wanted = TemplateModel.oppositeFacing(TemplateModel.facingOf(stepX, stepZ));
            if (!wanted.equals(Transforms.transformFacing(variant, childAnchor.facing()))) {
                return;
            }
            RoomPlacement child = placeAt(rooms.size(), childMeta, variant, childAnchor, target);
            if (child == null || !validRoom(child, parent, false)
                    || !validPath(path, parent, child, parentAnchor.height())) {
                return;
            }
            int floor = floorFor(parentFloor, declaredFloorDelta(parentAnchor, childAnchor, 0), child.originY());
            result.add(new Expansion(parent, parentAnchor, child, childAnchor,
                    new CorridorRun(-1, List.copyOf(path),
                            parentAnchor.width(), parentAnchor.height(), templateId),
                    stairs == 0 ? "corridor" : "staircase",
                    heightDiff, floor, false, expansionWeight(weight, stairs != 0)));
        }

        private void addLoops(List<Expansion> result, RoomPlacement parent, DoorAnchor parentAnchor,
                int[] from, String facing) {
            for (RoomPlacement other : rooms) {
                if (other.nodeId() == parent.nodeId() || linkedPairs.contains(pairKey(parent.nodeId(), other.nodeId()))) {
                    continue;
                }
                for (DoorAnchor otherAnchor : other.meta().anchors()) {
                    if (usedPorts.contains(portKey(other.nodeId(), otherAnchor.id()))
                            || !DoorCompatibility.from(parentAnchor).accepts(DoorCompatibility.from(otherAnchor))) {
                        continue;
                    }
                    int[] to = other.anchorWorld(otherAnchor);
                    String otherFacing = other.anchorFacing(otherAnchor);
                    if (!TemplateModel.oppositeFacing(facing).equals(otherFacing)) {
                        continue;
                    }
                    if (TemplateModel.isVertical(facing)) {
                        int delta = to[1] - from[1];
                        if (to[0] == from[0] && to[2] == from[2]
                                && delta != 0 && Integer.signum(delta) == TemplateModel.facingDy(facing)
                                && config.spatial.templateConnections()) {
                            result.add(new Expansion(parent, parentAnchor, other, otherAnchor, null,
                                    "template", delta, floors.getOrDefault(other.nodeId(), 1), true,
                                    config.spatial.loopChance()));
                        }
                        continue;
                    }
                    if (config.corridor.directAllowed()
                            && from[0] + TemplateModel.facingDx(facing) == to[0]
                            && from[2] + TemplateModel.facingDz(facing) == to[2]
                            && from[1] == to[1]) {
                        result.add(new Expansion(parent, parentAnchor, other, otherAnchor, null,
                                "direct", 0, floors.getOrDefault(other.nodeId(), 1), true,
                                config.spatial.loopChance()));
                    }
                    if (corridorAllowed(parentAnchor)) {
                        CorridorRun path = fixedCorridor(from, facing, to, otherFacing, parent, other,
                                parentAnchor);
                        if (path != null) {
                            result.add(new Expansion(parent, parentAnchor, other, otherAnchor, path,
                                    "corridor", to[1] - from[1], floors.getOrDefault(other.nodeId(), 1), true,
                                    config.spatial.loopChance()));
                        }
                    }
                }
            }
        }

        private CorridorRun fixedCorridor(int[] from, String fromFacing, int[] to, String toFacing,
                RoomPlacement parent, RoomPlacement other, DoorAnchor parentAnchor) {
            int[] start = {from[0] + TemplateModel.facingDx(fromFacing), from[1] - 1,
                    from[2] + TemplateModel.facingDz(fromFacing)};
            int[] end = {to[0] + TemplateModel.facingDx(toFacing), to[1] - 1,
                    to[2] + TemplateModel.facingDz(toFacing)};
            int heightDiff = start[1] - end[1];
            if (Math.abs(heightDiff) > config.corridor.maxHeightDiff()) {
                return null;
            }
            int dx = TemplateModel.facingDx(fromFacing);
            int dz = TemplateModel.facingDz(fromFacing);
            int ox = end[0] - start[0];
            int oz = end[2] - start[2];
            int along = ox * dx + oz * dz;
            int perpendicular = dx == 0 ? ox : oz;
            int stairs = Math.abs(heightDiff);
            List<CorridorCell> path = new ArrayList<>();
            int x = start[0];
            int y = start[1];
            int z = start[2];
            path.add(new CorridorCell(x, y, z, true));
            if (perpendicular == 0) {
                if (along < stairs + config.corridor.lengthMin()
                        || along > stairs + config.corridor.lengthMax()) {
                    return null;
                }
                for (int i = 0; i < along - stairs; i++) {
                    x += dx;
                    z += dz;
                    path.add(new CorridorCell(x, y, z, true));
                }
                for (int i = 0; i < stairs; i++) {
                    x += dx;
                    y -= Integer.signum(heightDiff);
                    z += dz;
                    path.add(new CorridorCell(x, y, z, true));
                }
            } else {
                if (config.corridor.maxTurns() < 1 || along < 1 || Math.abs(perpendicular) < stairs + 1) {
                    return null;
                }
                int pdx = dx == 0 ? Integer.signum(perpendicular) : 0;
                int pdz = dz == 0 ? Integer.signum(perpendicular) : 0;
                int second = Math.abs(perpendicular) - stairs;
                if (along + second < config.corridor.lengthMin()
                        || along + second > config.corridor.lengthMax()) {
                    return null;
                }
                for (int i = 0; i < along; i++) {
                    x += dx;
                    z += dz;
                    path.add(new CorridorCell(x, y, z, true));
                }
                for (int i = 0; i < stairs; i++) {
                    x += pdx;
                    y -= Integer.signum(heightDiff);
                    z += pdz;
                    path.add(new CorridorCell(x, y, z, true));
                }
                for (int i = 0; i < second; i++) {
                    x += pdx;
                    z += pdz;
                    path.add(new CorridorCell(x, y, z, true));
                }
            }
            if (x != end[0] || y != end[1] || z != end[2]
                    || !validPath(path, parent, other, parentAnchor.height())) {
                return null;
            }
            return new CorridorRun(-1, List.copyOf(path), parentAnchor.width(), parentAnchor.height(),
                    pickCorridorTemplate());
        }

        private Undo apply(Expansion expansion) {
            String firstPort = portKey(expansion.parent().nodeId(), expansion.parentAnchor().id());
            String secondPort = portKey(expansion.child().nodeId(), expansion.childAnchor().id());
            boolean addedFirstPort = usedPorts.add(firstPort);
            boolean addedSecondPort = usedPorts.add(secondPort);
            int oldCount = counts.getOrDefault(expansion.child().typeId(), 0);
            Integer oldFloor = floors.get(expansion.child().nodeId());
            if (!expansion.loop()) {
                rooms.add(expansion.child());
                byId.put(expansion.child().nodeId(), expansion.child());
                floors.put(expansion.child().nodeId(), expansion.floor());
                counts.put(expansion.child().typeId(), oldCount + 1);
            }
            LinkRecord link = new LinkRecord(links.size(), expansion.parent().nodeId(),
                    expansion.child().nodeId(), expansion.parentAnchor().id(), expansion.childAnchor().id(),
                    expansion.form(), expansion.heightDiff());
            links.add(link);
            linkedPairs.add(pairKey(link.parentNodeId(), link.childNodeId()));
            CorridorRun corridor = expansion.corridor();
            if (corridor != null) {
                corridor = new CorridorRun(corridors.size(), corridor.cells(),
                        corridor.sectionWidth(), corridor.sectionHeight(), corridor.templateId());
                corridors.add(corridor);
            }
            List<Long> roomCells = expansion.loop() ? List.of() : occupy(expansion.child());
            List<Long> pathCells = corridor == null ? List.of()
                    : occupyPath(corridor, corridor.sectionHeight());
            return new Undo(expansion.loop() ? null : expansion.child(), link, corridor,
                    firstPort, secondPort, addedFirstPort, addedSecondPort,
                    expansion.child().typeId(), oldCount, roomCells, pathCells, oldFloor);
        }

        private void undo(Undo change) {
            if (change.corridor() != null) {
                corridors.remove(change.corridor());
                for (Long cell : change.corridorCells()) {
                    corridorCells.remove(cell);
                }
            }
            links.remove(change.link());
            linkedPairs.remove(pairKey(change.link().parentNodeId(), change.link().childNodeId()));
            if (change.addedFirstPort()) {
                usedPorts.remove(change.firstPort());
            }
            if (change.addedSecondPort()) {
                usedPorts.remove(change.secondPort());
            }
            if (change.addedRoom() != null) {
                rooms.remove(change.addedRoom());
                byId.remove(change.addedRoom().nodeId());
                for (Long cell : change.roomCells()) {
                    occupied.remove(cell);
                }
                if (change.oldFloor() == null) {
                    floors.remove(change.addedRoom().nodeId());
                } else {
                    floors.put(change.addedRoom().nodeId(), change.oldFloor());
                }
                counts.put(change.typeId(), change.oldCount());
            }
        }

        private List<Long> occupy(RoomPlacement room) {
            List<Long> added = new ArrayList<>();
            for (long[] entry : room.meta().mask().cellsList()) {
                int[] local = Transforms.transform(room.meta(), room.variant(),
                        TemplateModel.unpackX(entry[0]), TemplateModel.unpackY(entry[0]), TemplateModel.unpackZ(entry[0]));
                long packed = TemplateModel.packCell(room.originX() + local[0], room.originY() + local[1],
                        room.originZ() + local[2]);
                if (!occupied.containsKey(packed)) {
                    occupied.put(packed, room.nodeId());
                    added.add(packed);
                }
            }
            return added;
        }

        private List<Long> occupyPath(CorridorRun run, int sectionHeight) {
            List<Long> added = new ArrayList<>();
            for (CorridorCell cell : run.cells()) {
                for (int y = 0; y <= sectionHeight; y++) {
                    long packed = TemplateModel.packCell(cell.x(), cell.y() + y, cell.z());
                    if (corridorCells.add(packed)) {
                        added.add(packed);
                    }
                }
            }
            return added;
        }

        private boolean validRoom(RoomPlacement candidate, RoomPlacement parent, boolean direct) {
            if (candidate.meta().mask() == null || outOfBounds(candidate)) {
                return false;
            }
            for (RoomPlacement existing : rooms) {
                if (direct && existing.nodeId() == parent.nodeId()) {
                    continue;
                }
                if (candidate.overlapsReal(existing)
                        || !(direct && candidate.sharesBoundary(existing))
                        && separatedBoxConflict(candidate, existing)) {
                    return false;
                }
            }
            for (long[] entry : candidate.meta().mask().cellsList()) {
                int[] local = Transforms.transform(candidate.meta(), candidate.variant(),
                        TemplateModel.unpackX(entry[0]), TemplateModel.unpackY(entry[0]), TemplateModel.unpackZ(entry[0]));
                long packed = TemplateModel.packCell(candidate.originX() + local[0], candidate.originY() + local[1],
                        candidate.originZ() + local[2]);
                if (occupied.containsKey(packed) || corridorCells.contains(packed)) {
                    return false;
                }
            }
            return true;
        }

        private boolean separatedBoxConflict(RoomPlacement a, RoomPlacement b) {
            int marginX = config.spatial.horizontalMargin();
            int marginY = config.spatial.verticalMargin();
            int aMaxX = a.originX() + Transforms.footprintX(a.meta(), a.variant()) - 1;
            int aMaxY = a.originY() + a.meta().sizeY() - 1;
            int aMaxZ = a.originZ() + Transforms.footprintZ(a.meta(), a.variant()) - 1;
            int bMaxX = b.originX() + Transforms.footprintX(b.meta(), b.variant()) - 1;
            int bMaxY = b.originY() + b.meta().sizeY() - 1;
            int bMaxZ = b.originZ() + Transforms.footprintZ(b.meta(), b.variant()) - 1;
            return a.originX() - marginX <= bMaxX && aMaxX + marginX >= b.originX()
                    && a.originY() - marginY <= bMaxY && aMaxY + marginY >= b.originY()
                    && a.originZ() - marginX <= bMaxZ && aMaxZ + marginX >= b.originZ();
        }

        private boolean validPath(List<CorridorCell> path, RoomPlacement parent, RoomPlacement child,
                int sectionHeight) {
            Set<Long> seen = new HashSet<>();
            for (CorridorCell cell : path) {
                for (int y = 0; y <= sectionHeight; y++) {
                    long packed = TemplateModel.packCell(cell.x(), cell.y() + y, cell.z());
                    if (!seen.add(packed) || corridorCells.contains(packed)) {
                        return false;
                    }
                    if (occupied.containsKey(packed)) {
                        Integer owner = occupied.get(packed);
                        if (owner == null || owner != parent.nodeId() && owner != child.nodeId()) {
                            return false;
                        }
                    }
                    if (maskAt(parent, cell.x(), cell.y() + y, cell.z())
                            || maskAt(child, cell.x(), cell.y() + y, cell.z())) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean maskAt(RoomPlacement room, int x, int y, int z) {
            int[] local = Transforms.transformInverse(room.meta(), room.variant(),
                    x - room.originX(), y - room.originY(), z - room.originZ());
            return room.meta().mask().has(local[0], local[1], local[2]);
        }

        private boolean validFinal() {
            if (roomCount() != targetRooms || !mandatoryValid() || missingFloors() > 0) {
                failure = "mandatory-or-floor-target";
                return false;
            }
            for (RoomPlacement room : rooms) {
                for (DoorAnchor anchor : room.meta().anchors()) {
                    if (!usedPorts.contains(portKey(room.nodeId(), anchor.id()))
                            && TemplateModel.POLICY_REJECT.equals(anchor.policyOrDefault(room.meta().doorPolicy()))) {
                        failure = "door-reject";
                        return false;
                    }
                }
            }
            return true;
        }

        private Layout finish() {
            List<UnconnectedAnchor> open = new ArrayList<>();
            for (RoomPlacement room : rooms) {
                for (DoorAnchor anchor : room.meta().anchors()) {
                    if (usedPorts.contains(portKey(room.nodeId(), anchor.id()))) {
                        continue;
                    }
                    open.add(new UnconnectedAnchor(room.nodeId(), room.typeId(), anchor.id(),
                            anchor.policyOrDefault(room.meta().doorPolicy()), room.anchorWorld(anchor),
                            room.anchorFacing(anchor), anchor.width(), anchor.height(), anchor.connectionKind()));
                }
            }
            return new Layout(List.copyOf(rooms), List.copyOf(corridors), List.copyOf(links),
                    List.copyOf(open), searchStates, derivedFloors());
        }

        private Map<Integer, Integer> derivedFloors() {
            java.util.TreeSet<Integer> heights = new java.util.TreeSet<>();
            Map<Integer, Integer> roomHeights = new LinkedHashMap<>();
            for (RoomPlacement room : rooms) {
                roomHeights.put(room.nodeId(), room.originY());
                heights.add(room.originY());
            }
            Map<Integer, Integer> levels = new LinkedHashMap<>();
            int level = 1;
            for (Integer height : heights) {
                levels.put(height, level++);
            }
            Map<Integer, Integer> result = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> entry : roomHeights.entrySet()) {
                result.put(entry.getKey(), levels.getOrDefault(entry.getValue(), 1));
            }
            return Map.copyOf(result);
        }

        private boolean mandatoryValid() {
            for (Assigner.TemplateOption option : options) {
                if (!isRoom(option.typeId())) {
                    continue;
                }
                int count = counts.getOrDefault(option.typeId(), 0);
                if (count < option.minCount() || option.maxCount() > 0 && count > option.maxCount()) {
                    return false;
                }
            }
            Map<Integer, Integer> depths = depths();
            int deepest = rooms.stream().mapToInt(room -> depths.getOrDefault(room.nodeId(), 0)).max().orElse(0);
            for (Assigner.MandatoryRule rule : rules) {
                int count = mandatoryCount(rule);
                if (count < Math.max(1, rule.minCount()) || rule.maxCount() > 0 && count > rule.maxCount()) {
                    return false;
                }
                if (count == 0) {
                    continue;
                }
                if ("root".equals(rule.placementRule())) {
                    RoomPlacement root = byId.get(0);
                    if (root == null || !metas.get(root.typeId()).tags().contains(rule.tag())) {
                        return false;
                    }
                } else if ("deepest".equals(rule.placementRule())) {
                    boolean found = false;
                    for (RoomPlacement room : rooms) {
                        if (isRoom(room.typeId()) && depths.getOrDefault(room.nodeId(), 0) == deepest
                                && room.meta().tags().contains(rule.tag())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }
                } else if ("branch-percentile".equals(rule.placementRule())) {
                    int min = targetRooms * rule.percentile() / 100;
                    int max = Math.min(targetRooms, targetRooms * (rule.percentile() + 20) / 100);
                    boolean found = false;
                    for (RoomPlacement room : rooms) {
                        int depth = depths.getOrDefault(room.nodeId(), 0);
                        if (isRoom(room.typeId()) && depth >= min && depth <= max
                                && room.meta().tags().contains(rule.tag())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }
                }
            }
            return true;
        }

        private int mandatoryCount(Assigner.MandatoryRule rule) {
            int count = 0;
            for (RoomPlacement room : rooms) {
                if (isRoom(room.typeId()) && room.meta().tags().contains(rule.tag())) {
                    count++;
                }
            }
            return count;
        }

        private Map<Integer, Integer> depths() {
            Map<Integer, List<Integer>> adjacency = new HashMap<>();
            for (LinkRecord link : links) {
                adjacency.computeIfAbsent(link.parentNodeId(), key -> new ArrayList<>()).add(link.childNodeId());
                adjacency.computeIfAbsent(link.childNodeId(), key -> new ArrayList<>()).add(link.parentNodeId());
            }
            Map<Integer, Integer> result = new HashMap<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            result.put(0, 0);
            queue.add(0);
            while (!queue.isEmpty()) {
                int current = queue.poll();
                for (int next : adjacency.getOrDefault(current, List.of())) {
                    if (!result.containsKey(next)) {
                        result.put(next, result.get(current) + 1);
                        queue.add(next);
                    }
                }
            }
            return result;
        }

        private int roomCount() {
            int count = 0;
            for (RoomPlacement room : rooms) {
                if (isRoom(room.typeId())) {
                    count++;
                }
            }
            return count;
        }

        private int nodeCapacity() {
            return targetRooms * (config.spatial.floorCount() + 1) + 2;
        }

        private boolean nodesExceeded() {
            return rooms.size() >= nodeCapacity();
        }

        private boolean floorAllowed(int current, int delta) {
            int target = current + delta;
            return target >= 1 && target <= floorTarget();
        }

        private boolean hasVerticalDeclaration(DoorAnchor first, DoorAnchor second) {
            return first.floorDelta() != null && first.floorDelta() != 0
                    || second.floorDelta() != null && second.floorDelta() != 0
                    || first.verticalDelta() != null && first.verticalDelta() != 0
                    || second.verticalDelta() != null && second.verticalDelta() != 0;
        }

        private boolean isRoom(String id) {
            TemplateMeta meta = metas.get(id);
            return meta != null && TemplateModel.TYPE_ROOM.equals(meta.structureType());
        }

        private List<Transforms.Variant> variants(TemplateMeta meta) {
            List<Transforms.Variant> result = new ArrayList<>();
            int[] rotations = config.orientation.rotatable() && meta.rotatable()
                    ? new int[] {0, 90, 180, 270} : new int[] {0};
            for (int rotation : rotations) {
                result.add(new Transforms.Variant(rotation, false));
                if (config.orientation.mirrorAxis().equals("x") && meta.mirrorable()) {
                    result.add(new Transforms.Variant(rotation, true));
                }
            }
            return result;
        }

        private RoomPlacement placeAt(int nodeId, TemplateMeta meta, Transforms.Variant variant,
                DoorAnchor anchor, int[] target) {
            int[] local = Transforms.transform(meta, variant, anchor.x(), anchor.y(), anchor.z());
            return new RoomPlacement(nodeId, meta.id(), meta, variant,
                    target[0] - local[0], target[1] - local[1], target[2] - local[2]);
        }

        private int declaredFloorDelta(DoorAnchor parent, DoorAnchor child, int direction) {
            if (parent.floorDelta() != null && child.floorDelta() != null
                    && !parent.floorDelta().equals(child.floorDelta())) {
                return 0;
            }
            Integer declared = parent.floorDelta() != null ? parent.floorDelta() : child.floorDelta();
            return declared != null ? declared : direction;
        }

        private int floorFor(int parentFloor, int declared, int originY) {
            if (declared != 0) {
                return parentFloor + declared;
            }
            int sameLevel = sameLevelDelta();
            if (sameLevel <= 0) {
                return parentFloor;
            }
            return parentFloor + Math.floorDiv(originY - baseY, sameLevel);
        }

        private int sameLevelDelta() {
            int margin = config.spatial.verticalMargin();
            return Math.max(1, margin);
        }

        /**
         * 水平走廊两端门的高差。
         *
         * <p>默认恒为 0: 门与门必须完整贴合, 同层房间的地板与门洞绝对齐平。楼层与高度差
         * 只由模板自身的门锚点 Y 位置决定 (例如玩家做的双层走廊, 两层门 Y 不同), 引擎不再
         * 随机抬升走廊, 否则同一层的房间会高矮不一且门与门错位。
         */
        private int chooseHeight(DoorAnchor parent, DoorAnchor child) {
            Integer explicit = parent.verticalDelta() != null ? parent.verticalDelta() : child.verticalDelta();
            return explicit != null ? explicit : 0;
        }

        /**
         * 走廊段的格数, 尽量取走廊模板块长度的整数倍。
         *
         * <p>两个理由: 段长比模板短时一整块模板都放不下, 这一段会退化成纯程序化地板;
         * 段长不是模板块长度的整数倍时, 最后一块只能铺到一半, 收尾门框会被截断, 外观上
         * 就是走廊与房间之间"差两格"。
         */
        private int randomLength() {
            int min = Math.max(1, config.corridor.lengthMin());
            int max = Math.max(min, config.corridor.lengthMax());
            int tile = shortestCorridorTile();
            if (tile > 0 && max >= tile) {
                int first = Math.max(1, (min + tile - 1) / tile);
                int last = max / tile;
                if (last >= first) {
                    return (first + random.nextInt(last - first + 1)) * tile;
                }
            }
            return min + random.nextInt(max - min + 1);
        }

        /** 把长度向上取整到模板块长度的整数倍; 无模板时原样返回。 */
        private int roundUpToTile(int value, int tile) {
            if (tile <= 0) {
                return Math.max(1, value);
            }
            return Math.max(tile, (value + tile - 1) / tile * tile);
        }

        /** 可抽样走廊模板中最短的一块的长度; 无可抽样模板时返回 0。 */
        private int shortestCorridorTile() {
            int shortest = 0;
            for (Assigner.TemplateOption option : corridorOptions) {
                TemplateMeta meta = metas.get(option.typeId());
                int length = meta == null ? 0 : CorridorAssembler.tileLength(meta);
                if (length > 0 && (shortest == 0 || length < shortest)) {
                    shortest = length;
                }
            }
            return shortest;
        }

        /**
         * 该门锚点能否走走廊。
         *
         * <p>不再与固定配置截面比对: 门宽高由采集框选实测, 走廊截面直接跟随所用门,
         * 因此不同尺寸的门可以各自生成对应截面的走廊。
         */
        private boolean corridorAllowed(DoorAnchor anchor) {
            if (anchor == null || anchor.width() < 1 || anchor.height() < 1) {
                return false;
            }
            if (!config.corridor.corridorEnabled()) {
                return false;
            }
            if (config.spatial.proceduralConnections()) {
                return true;
            }
            for (TemplateMeta meta : metas.values()) {
                if (TemplateModel.TYPE_CORRIDOR.equals(meta.structureType())) {
                    return true;
                }
            }
            return false;
        }

        /** 展开候选的抽样权重; 竖向连接按 vertical-chance 缩放, 避免跨层段挤掉水平段。 */
        private double expansionWeight(int weight, boolean vertical) {
            double base = Math.max(1, weight);
            if (vertical) {
                base *= Math.max(0.01D, config.spatial.verticalChance() / 50.0D);
            }
            return base;
        }

        private List<Expansion> weighted(List<Expansion> source) {
            Map<Expansion, Double> scores = new HashMap<>();
            for (Expansion expansion : source) {
                double weight = Math.max(0.0001D, expansion.weight());
                scores.put(expansion, -Math.log(Math.max(Double.MIN_VALUE, random.nextDouble())) / weight);
            }
            List<Expansion> result = new ArrayList<>(source);
            result.sort((a, b) -> Double.compare(scores.get(a), scores.get(b)));
            return result;
        }

        private List<RoomPlacement> shuffledRoots(List<RoomPlacement> source) {
            List<RoomPlacement> result = new ArrayList<>(source);
            Collections.shuffle(result, random);
            return result;
        }

        private boolean outOfBounds(RoomPlacement room) {
            return Math.abs(room.originX()) > 1_000_000 || Math.abs(room.originZ()) > 1_000_000
                    || room.originY() < -1_000_000 || room.originY() > 1_000_000;
        }

        private String portKey(int nodeId, String anchorId) {
            return nodeId + ":" + anchorId;
        }

        private String pairKey(int a, int b) {
            return Math.min(a, b) + ":" + Math.max(a, b);
        }

        private int valueOrZero(Integer first, Integer second) {
            return first != null ? first : second == null ? 0 : second;
        }
    }
}
