package emaki.jiuwu.craft.dungeon.random.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;

public final class Placement {

    public record RoomPlacement(int nodeId, String typeId, TemplateMeta meta,
            Transforms.Variant variant, int originX, int originY, int originZ) {

        public boolean overlapsReal(RoomPlacement other) {
            if (meta.mask() == null || other.meta.mask() == null) {
                return false;
            }
            for (long[] entry : meta.mask().cellsList()) {
                int[] world = Transforms.transform(meta, variant,
                        emaki.jiuwu.craft.dungeon.random.model.TemplateModel.unpackX(entry[0]),
                        emaki.jiuwu.craft.dungeon.random.model.TemplateModel.unpackY(entry[0]),
                        emaki.jiuwu.craft.dungeon.random.model.TemplateModel.unpackZ(entry[0]));
                int[] local = Transforms.transformInverse(other.meta(), other.variant(),
                        originX + world[0] - other.originX,
                        originY + world[1] - other.originY,
                        originZ + world[2] - other.originZ);
                if (other.meta.mask().has(local[0], local[1], local[2])) {
                    return true;
                }
            }
            return false;
        }

        public int[] anchorWorld(DoorAnchor anchor) {
            int[] local = Transforms.transform(meta, variant, anchor.x(), anchor.y(), anchor.z());
            return new int[] {originX + local[0], originY + local[1], originZ + local[2]};
        }

        public String anchorFacing(DoorAnchor anchor) {
            return Transforms.transformFacing(variant, anchor.facing());
        }

        public List<int[]> anchorCells(DoorAnchor anchor) {
            List<int[]> result = new ArrayList<>();
            for (int[] cell : AnchorGeometry.openingCells(anchor)) {
                int[] local = Transforms.transform(meta, variant, cell[0], cell[1], cell[2]);
                result.add(new int[] {originX + local[0], originY + local[1], originZ + local[2]});
            }
            return result;
        }

        public boolean boxIntersects(RoomPlacement other) {
            return intersectsBox(other);
        }

        private boolean intersectsBox(RoomPlacement other) {
            int thisMaxX = originX + Transforms.footprintX(meta, variant) - 1;
            int thisMaxZ = originZ + Transforms.footprintZ(meta, variant) - 1;
            int otherMaxX = other.originX + Transforms.footprintX(other.meta, other.variant) - 1;
            int otherMaxZ = other.originZ + Transforms.footprintZ(other.meta, other.variant) - 1;
            return originX <= otherMaxX && thisMaxX >= other.originX
                    && originY + meta.sizeY() - 1 >= other.originY && originY <= other.originY + other.meta.sizeY() - 1
                    && originZ <= otherMaxZ && thisMaxZ >= other.originZ;
        }

        public boolean sharesBoundary(RoomPlacement other) {
            int thisMaxX = originX + Transforms.footprintX(meta, variant) - 1;
            int thisMaxZ = originZ + Transforms.footprintZ(meta, variant) - 1;
            int otherMaxX = other.originX + Transforms.footprintX(other.meta, other.variant) - 1;
            int otherMaxZ = other.originZ + Transforms.footprintZ(other.meta, other.variant) - 1;
            boolean xTouch = thisMaxX + 1 == other.originX || otherMaxX + 1 == originX;
            boolean zTouch = thisMaxZ + 1 == other.originZ || otherMaxZ + 1 == originZ;
            boolean yOverlap = originY + meta.sizeY() - 1 >= other.originY
                    && originY <= other.originY + other.meta.sizeY() - 1;
            boolean xOverlap = originX <= otherMaxX && thisMaxX >= other.originX;
            boolean zOverlap = originZ <= otherMaxZ && thisMaxZ >= other.originZ;
            boolean yTouch = originY + meta.sizeY() == other.originY
                || other.originY + other.meta.sizeY() == originY;
        return (xTouch && yOverlap && zOverlap) || (zTouch && yOverlap && xOverlap)
                || (yTouch && xOverlap && zOverlap);
        }
    }

    public record CorridorCell(int x, int y, int z, boolean floorCell) {
    }

    public record CorridorRun(int edgeIndex, List<CorridorCell> cells) {
    }

    public record LinkRecord(int edgeIndex, int parentNodeId, int childNodeId,
            String parentAnchorId, String childAnchorId, String form, int heightDiff) {
    }

    public record UnconnectedAnchor(int nodeId, String typeId, String anchorId, String policy,
            int[] worldPos, String facing, int width, int height, String connectionKind) {

        public UnconnectedAnchor(int nodeId, String typeId, String anchorId, String policy,
                int[] worldPos, String facing, int width, int height) {
            this(nodeId, typeId, anchorId, policy, worldPos, facing, width, height, "walkway");
        }
    }

    public record Layout(List<RoomPlacement> rooms, List<CorridorRun> corridors,
            List<LinkRecord> links, List<UnconnectedAnchor> unconnected, int attempts,
            Map<Integer, Integer> logicalFloors) {

        public Layout(List<RoomPlacement> rooms, List<CorridorRun> corridors,
                List<LinkRecord> links, List<UnconnectedAnchor> unconnected, int attempts) {
            this(rooms, corridors, links, unconnected, attempts, Map.of());
        }

        public int floorOf(int nodeId) {
            return logicalFloors.getOrDefault(nodeId, 1);
        }
    }

    private Placement() {
    }
}
