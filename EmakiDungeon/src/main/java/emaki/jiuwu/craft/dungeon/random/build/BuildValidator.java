package emaki.jiuwu.craft.dungeon.random.build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import emaki.jiuwu.craft.dungeon.random.build.BuildPipeline.Plan;
import emaki.jiuwu.craft.dungeon.random.build.BuildPipeline.PlannedBlock;
import emaki.jiuwu.craft.dungeon.random.layout.AnchorGeometry;
import emaki.jiuwu.craft.dungeon.random.layout.Placement;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.Layout;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.LinkRecord;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.RoomPlacement;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;

public final class BuildValidator {

    public record Report(List<String> failures) {

        public boolean passed() {
            return failures.isEmpty();
        }
    }

    public Report validate(World world, Layout layout, Plan plan) {
        List<String> failures = new ArrayList<>();
        checkWalkConnectivity(world, layout, failures);
        checkAnchorDocking(world, layout, failures);
        checkSeals(world, layout, failures);
        checkMaskRestore(world, plan, failures);
        return new Report(List.copyOf(failures));
    }

    private void checkWalkConnectivity(World world, Layout layout, List<String> failures) {
        for (LinkRecord link : layout.links()) {
            RoomPlacement from = roomOf(layout, link.parentNodeId());
            RoomPlacement to = roomOf(layout, link.childNodeId());
            if (from == null || to == null) {
                failures.add("walkability: 链接房间缺失 " + link.edgeIndex());
                continue;
            }
            DoorAnchor fromAnchor = from.meta().anchor(link.parentAnchorId());
            DoorAnchor toAnchor = to.meta().anchor(link.childAnchorId());
            if (fromAnchor == null || toAnchor == null) {
                failures.add("walkability: 链接锚点缺失 " + link.parentAnchorId() + "->" + link.childAnchorId());
                continue;
            }
            int[] start = from.anchorWorld(fromAnchor);
            int[] goal = to.anchorWorld(toAnchor);
            if (!reachable(world, start, goal)) {
                failures.add("walkability: 不可步行 " + link.parentNodeId() + "->" + link.childNodeId());
            }
        }
    }

    private void checkAnchorDocking(World world, Layout layout, List<String> failures) {
        for (LinkRecord link : layout.links()) {
            RoomPlacement from = roomOf(layout, link.parentNodeId());
            RoomPlacement to = roomOf(layout, link.childNodeId());
            if (from == null || to == null) {
                continue;
            }
            DoorAnchor fromAnchor = from.meta().anchor(link.parentAnchorId());
            DoorAnchor toAnchor = to.meta().anchor(link.childAnchorId());
            if (fromAnchor == null || toAnchor == null) {
                continue;
            }
            int[] start = from.anchorWorld(fromAnchor);
            int[] goal = to.anchorWorld(toAnchor);
            for (int[] cell : from.anchorCells(fromAnchor)) {
                Block block = world.getBlockAt(cell[0], cell[1], cell[2]);
                if (!block.getType().isAir() && !block.isPassable()) {
                    failures.add("docking: 父锚点开口被阻塞 " + link.parentAnchorId());
                    break;
                }
            }
            for (int[] cell : to.anchorCells(toAnchor)) {
                Block block = world.getBlockAt(cell[0], cell[1], cell[2]);
                if (!block.getType().isAir() && !block.isPassable()) {
                    failures.add("docking: 子锚点开口被阻塞 " + link.childAnchorId());
                    break;
                }
            }
            if ("direct".equals(link.form())) {
                if (Math.abs(start[1] - goal[1]) > (TemplateModel.isVertical(from.anchorFacing(fromAnchor)) ? 1 : 0)
                        || start[0] + TemplateModel.facingDx(from.anchorFacing(fromAnchor)) != goal[0]
                        || start[2] + TemplateModel.facingDz(from.anchorFacing(fromAnchor)) != goal[2]) {
                    failures.add("docking: 直连对未对齐 " + link.parentAnchorId() + "->" + link.childAnchorId());
                }
                if (fromAnchor.width() != toAnchor.width() || fromAnchor.height() != toAnchor.height()) {
                    failures.add("docking: 直连截面不一致 " + link.parentAnchorId() + "->" + link.childAnchorId());
                }
            }
        }
    }

    private void checkSeals(World world, Layout layout, List<String> failures) {
        for (var unconnected : layout.unconnected()) {
            boolean climbable = "ladder".equals(unconnected.connectionKind())
                    || "elevator".equals(unconnected.connectionKind());
            if (climbable) {
                for (int[] cell : unconnectedCells(unconnected)) {
                    Block block = world.getBlockAt(cell[0], cell[1], cell[2]);
                    if (!block.getType().isAir() && !block.isPassable()
                            && block.getType() != org.bukkit.Material.LADDER
                            && block.getType() != org.bukkit.Material.BUBBLE_COLUMN) {
                        failures.add("seal: 垂直连接不可攀爬 " + unconnected.anchorId());
                    }
                }
                continue;
            }
            checkSealCells(world, unconnected, failures, unconnectedCells(unconnected));
        }
    }

    private List<int[]> unconnectedCells(Placement.UnconnectedAnchor anchor) {
        return AnchorGeometry.openingCells(
                new DoorAnchor(anchor.anchorId(), anchor.worldPos()[0], anchor.worldPos()[1], anchor.worldPos()[2],
                        anchor.facing(), anchor.width(), anchor.height(), anchor.worldPos()[1], 1,
                        anchor.policy(), ""));
    }

    private void checkSealCells(World world, Placement.UnconnectedAnchor unconnected,
            List<String> failures, List<int[]> cells) {
        for (int[] cell : cells) {
            Block block = world.getBlockAt(cell[0], cell[1], cell[2]);
            if (TemplateModel.POLICY_SEAL.equals(unconnected.policy())
                    && (block.getType().isAir() || block.isPassable())
                    || TemplateModel.POLICY_KEEP.equals(unconnected.policy())
                    && !block.getType().isAir() && !block.isPassable()) {
                failures.add("seal: 门格状态错误 " + unconnected.anchorId());
                return;
            }
        }
    }

    private void checkMaskRestore(World world, Plan plan, List<String> failures) {
        for (PlannedBlock block : plan.rooms()) {
            BlockData actual = world.getBlockAt(block.x(), block.y(), block.z()).getBlockData();
            if (!actual.getAsString().equals(block.data().getAsString())) {
                failures.add("mask: 还原不一致 " + block.x() + "," + block.y() + "," + block.z());
                if (failures.size() > 32) {
                    return;
                }
            }
        }
    }

    private boolean reachable(World world, int[] start, int[] goal) {
        if (!standing(world, start[0], start[1], start[2]) || !standing(world, goal[0], goal[1], goal[2])) {
            return false;
        }
        Set<Long> visited = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        long startKey = key(start[0], start[1], start[2]);
        visited.add(startKey);
        queue.add(new int[] {start[0], start[1], start[2]});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            if (current[0] == goal[0] && current[1] == goal[1] && current[2] == goal[2]) {
                return true;
            }
            for (int[] step : steps) {
                for (int dy = -1; dy <= 1; dy++) {
                    int x = current[0] + step[0];
                    int y = current[1] + dy;
                    int z = current[2] + step[1];
                    long packed = key(x, y, z);
                    if (visited.contains(packed) || !standing(world, x, y, z)) {
                        continue;
                    }
                    visited.add(packed);
                    queue.add(new int[] {x, y, z});
                }
            }
        }
        return false;
    }

    private boolean standing(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        if (!feet.getType().isAir() && !feet.isPassable()) {
            return false;
        }
        Block head = world.getBlockAt(x, y + 1, z);
        if (!head.getType().isAir() && !head.isPassable()) {
            return false;
        }
        Block floor = world.getBlockAt(x, y - 1, z);
        return !floor.getType().isAir() && !floor.isPassable();
    }

    private long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (long) (z & 0x3FFFFF);
    }

    private RoomPlacement roomOf(Layout layout, int nodeId) {
        for (RoomPlacement room : layout.rooms()) {
            if (room.nodeId() == nodeId) {
                return room;
            }
        }
        return null;
    }
}
