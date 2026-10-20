package emaki.jiuwu.craft.dungeon.flow;

public record FlowRegion(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public boolean contains(double x, double y, double z) {
        return x >= Math.min(minX, maxX) && x <= Math.max(minX, maxX)
                && y >= Math.min(minY, maxY) && y <= Math.max(minY, maxY)
                && z >= Math.min(minZ, maxZ) && z <= Math.max(minZ, maxZ);
    }

    public boolean contains(FlowPoint point) {
        return point != null && contains(point.x(), point.y(), point.z());
    }

    public FlowPoint center() {
        return FlowPoint.of((minX + maxX) / 2.0D, (minY + maxY) / 2.0D, (minZ + maxZ) / 2.0D);
    }
}
