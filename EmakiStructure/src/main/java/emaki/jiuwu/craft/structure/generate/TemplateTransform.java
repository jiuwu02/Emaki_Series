package emaki.jiuwu.craft.structure.generate;

import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.util.Vector;

public final class TemplateTransform {

    private TemplateTransform() {
    }

    public static Vector localToWorld(int x, int y, int z, int sizeX, int sizeZ, StructureFacing facing) {
        int mirroredX = facing.mirrored() ? sizeX - 1 - x : x;
        return switch (facing.rotationQuarterTurns()) {
            case 0 -> new Vector(mirroredX, y, z);
            case 1 -> new Vector(sizeZ - 1 - z, y, mirroredX);
            case 2 -> new Vector(sizeX - 1 - mirroredX, y, sizeZ - 1 - z);
            default -> new Vector(z, y, sizeX - 1 - mirroredX);
        };
    }

    public static int transformedSizeX(int sizeX, int sizeZ, StructureFacing facing) {
        return facing.rotationQuarterTurns() % 2 == 0 ? sizeX : sizeZ;
    }

    public static int transformedSizeZ(int sizeX, int sizeZ, StructureFacing facing) {
        return facing.rotationQuarterTurns() % 2 == 0 ? sizeZ : sizeX;
    }

    public static BlockData apply(BlockData original, StructureFacing facing) {
        if (!facing.mirrored() && facing.rotationQuarterTurns() == 0) {
            return original;
        }
        BlockData copy = original.clone();
        if (facing.mirrored()) {
            copy.mirror(Mirror.LEFT_RIGHT);
        }
        copy.rotate(rotationOf(facing));
        return copy;
    }

    private static StructureRotation rotationOf(StructureFacing facing) {
        return switch (facing.rotationQuarterTurns()) {
            case 0 -> StructureRotation.NONE;
            case 1 -> StructureRotation.CLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_180;
            default -> StructureRotation.COUNTERCLOCKWISE_90;
        };
    }

    public static float combinedYaw(float templateYaw, StructureFacing facing) {
        float result = templateYaw + facing.yawDegrees();
        float normalized = result % 360.0f;
        return normalized < 0.0f ? normalized + 360.0f : normalized;
    }
}
