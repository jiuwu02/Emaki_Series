package emaki.jiuwu.craft.structure.generate;

import java.util.List;
import java.util.Locale;

public enum StructureFacing {

    NORTH(false, 180),
    EAST(false, 270),
    SOUTH(false, 0),
    WEST(false, 90),
    NORTH_MIRROR(true, 180),
    EAST_MIRROR(true, 270),
    SOUTH_MIRROR(true, 0),
    WEST_MIRROR(true, 90);

    private static final List<String> CONFIG_VALUES = List.of(
            "north", "east", "south", "west",
            "north_mirror", "east_mirror", "south_mirror", "west_mirror");

    private final boolean mirrored;
    private final int yawDegrees;

    StructureFacing(boolean mirrored, int yawDegrees) {
        this.mirrored = mirrored;
        this.yawDegrees = yawDegrees;
    }

    public static List<String> configValues() {
        return CONFIG_VALUES;
    }

    public static StructureFacing parse(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (StructureFacing value : values()) {
            if (value.configValue().equals(normalized)) {
                return value;
            }
        }
        return null;
    }

    public static StructureFacing fromYaw(float yaw, boolean mirrored) {
        int quadrant = Math.round(yaw / 90.0f);
        int normalized = Math.floorMod(quadrant, 4);
        StructureFacing base = switch (normalized) {
            case 0 -> SOUTH;
            case 1 -> WEST;
            case 2 -> NORTH;
            default -> EAST;
        };
        return mirrored ? base.mirrorVariant() : base;
    }

    public String configValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean mirrored() {
        return mirrored;
    }

    public int rotationQuarterTurns() {
        return switch (this) {
            case SOUTH, SOUTH_MIRROR -> 0;
            case WEST, WEST_MIRROR -> 1;
            case NORTH, NORTH_MIRROR -> 2;
            default -> 3;
        };
    }

    public StructureFacing mirrored(boolean applyMirror) {
        StructureFacing base = unmirrored();
        return applyMirror ? base.mirrorVariant() : base;
    }

    public StructureFacing unmirrored() {
        return mirrored ? StructureFacing.valueOf(name().replace("_MIRROR", "")) : this;
    }

    public StructureFacing mirrorVariant() {
        return mirrored ? this : StructureFacing.valueOf(name() + "_MIRROR");
    }

    public int yawDegrees() {
        return yawDegrees;
    }
}
