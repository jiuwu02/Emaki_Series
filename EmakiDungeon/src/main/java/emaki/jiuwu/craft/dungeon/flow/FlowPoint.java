package emaki.jiuwu.craft.dungeon.flow;

public record FlowPoint(double x, double y, double z, float yaw, float pitch) {

    public static FlowPoint of(double x, double y, double z) {
        return new FlowPoint(x, y, z, 0.0F, 0.0F);
    }
}
