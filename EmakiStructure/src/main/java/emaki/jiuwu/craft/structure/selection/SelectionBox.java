package emaki.jiuwu.craft.structure.selection;

import java.util.Optional;

public record SelectionBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    private record Interval(int low, int high) {
    }

    public static SelectionBox of(int firstX, int firstY, int firstZ, int secondX, int secondY, int secondZ) {
        return new SelectionBox(
                Math.min(firstX, secondX), Math.min(firstY, secondY), Math.min(firstZ, secondZ),
                Math.max(firstX, secondX), Math.max(firstY, secondY), Math.max(firstZ, secondZ));
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean withinLimits(int limitX, int limitY, int limitZ) {
        return sizeX() <= limitX && sizeY() <= limitY && sizeZ() <= limitZ;
    }

    public Optional<SelectionBox> adjust(AdjustDirection.Axis axis, boolean positiveDirection,
            boolean grow, int amount) {
        if (amount <= 0) {
            return Optional.empty();
        }
        return switch (axis) {
            case Y -> shift(minY, maxY, positiveDirection, grow, amount)
                    .map(interval -> new SelectionBox(minX, interval.low(), minZ,
                            maxX, interval.high(), maxZ));
            case X -> shift(minX, maxX, positiveDirection, grow, amount)
                    .map(interval -> new SelectionBox(interval.low(), minY, minZ,
                            interval.high(), maxY, maxZ));
            case Z -> shift(minZ, maxZ, positiveDirection, grow, amount)
                    .map(interval -> new SelectionBox(minX, minY, interval.low(),
                            maxX, maxY, interval.high()));
        };
    }

    private static Optional<Interval> shift(int low, int high, boolean positiveDirection,
            boolean grow, int amount) {
        if (grow) {
            if (positiveDirection) {
                if (high > Integer.MAX_VALUE - amount) {
                    return Optional.empty();
                }
                return Optional.of(new Interval(low, high + amount));
            }
            if (low < Integer.MIN_VALUE + amount) {
                return Optional.empty();
            }
            return Optional.of(new Interval(low - amount, high));
        }
        if (high - low + 1 - amount < 1) {
            return Optional.empty();
        }
        return positiveDirection
                ? Optional.of(new Interval(low, high - amount))
                : Optional.of(new Interval(low + amount, high));
    }
}
