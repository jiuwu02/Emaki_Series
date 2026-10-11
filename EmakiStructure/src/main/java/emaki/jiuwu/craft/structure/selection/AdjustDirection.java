package emaki.jiuwu.craft.structure.selection;

import java.util.List;
import java.util.Locale;

public enum AdjustDirection {

    FRONT,
    BACK,
    LEFT,
    RIGHT,
    UP,
    DOWN;

    public enum Axis {
        X, Y, Z
    }

    public record AxisVector(Axis axis, boolean positive) {
    }

    private static final List<String> CONFIG_VALUES = List.of("front", "back", "left", "right", "up", "down");

    public static List<String> configValues() {
        return CONFIG_VALUES;
    }

    public static AdjustDirection parse(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (AdjustDirection value : values()) {
            if (value.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return value;
            }
        }
        return null;
    }

    public AxisVector resolve(float yaw) {
        return switch (this) {
            case UP -> new AxisVector(Axis.Y, true);
            case DOWN -> new AxisVector(Axis.Y, false);
            case FRONT -> facing(yaw, 0);
            case RIGHT -> facing(yaw, 1);
            case BACK -> facing(yaw, 2);
            case LEFT -> facing(yaw, 3);
        };
    }

    private static AxisVector facing(float yaw, int turn) {
        int index = Math.floorMod(Math.round(yaw / 90.0f) + turn, 4);
        return switch (index) {
            case 0 -> new AxisVector(Axis.Z, true);
            case 1 -> new AxisVector(Axis.X, false);
            case 2 -> new AxisVector(Axis.Z, false);
            default -> new AxisVector(Axis.X, true);
        };
    }
}
