package emaki.jiuwu.craft.dungeon.random.layout;

import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;

public final class Transforms {

    public record Variant(int rotations, boolean mirror) {

        public String key() {
            return rotations + (mirror ? "m" : "");
        }
    }

    public static int footprintX(TemplateMeta meta, Variant variant) {
        return variant.rotations() % 2 == 0 ? meta.sizeX() : meta.sizeZ();
    }

    public static int footprintZ(TemplateMeta meta, Variant variant) {
        return variant.rotations() % 2 == 0 ? meta.sizeZ() : meta.sizeX();
    }

    public static int[] transform(TemplateMeta meta, Variant variant, int x, int y, int z) {
        int lx = variant.mirror() ? meta.sizeX() - 1 - x : x;
        int lz = z;
        return switch (Math.floorMod(variant.rotations(), 4)) {
            case 1 -> new int[] {meta.sizeZ() - 1 - lz, y, lx};
            case 2 -> new int[] {meta.sizeX() - 1 - lx, y, meta.sizeZ() - 1 - lz};
            case 3 -> new int[] {lz, y, meta.sizeX() - 1 - lx};
            default -> new int[] {lx, y, lz};
        };
    }

    public static double[] transformExact(TemplateMeta meta, Variant variant, double x, double y, double z) {
        double lx = variant.mirror() ? meta.sizeX() - x : x;
        double lz = z;
        return switch (Math.floorMod(variant.rotations(), 4)) {
            case 1 -> new double[] {meta.sizeZ() - lz, y, lx};
            case 2 -> new double[] {meta.sizeX() - lx, y, meta.sizeZ() - lz};
            case 3 -> new double[] {lz, y, meta.sizeX() - lx};
            default -> new double[] {lx, y, lz};
        };
    }

    public static int[] transformInverse(TemplateMeta meta, Variant variant, int x, int y, int z) {
        int x1;
        int z1;
        switch (Math.floorMod(variant.rotations(), 4)) {
            case 1 -> {
                x1 = z;
                z1 = meta.sizeZ() - 1 - x;
            }
            case 2 -> {
                x1 = meta.sizeX() - 1 - x;
                z1 = meta.sizeZ() - 1 - z;
            }
            case 3 -> {
                x1 = meta.sizeX() - 1 - z;
                z1 = x;
            }
            default -> {
                x1 = x;
                z1 = z;
            }
        }
        int lx = variant.mirror() ? meta.sizeX() - 1 - x1 : x1;
        return new int[] {lx, y, z1};
    }

    public static String transformFacing(Variant variant, String facing) {
        String result = variant.mirror() ? TemplateModel.mirrorFacing(facing) : facing;
        return TemplateModel.rotateFacing(result, variant.rotations());
    }

    public static boolean[] transformVector(Variant variant, int dx, int dz) {
        int rx = dx;
        int rz = dz;
        if (variant.mirror()) {
            rx = -rx;
        }
        int[] rotated = switch (Math.floorMod(variant.rotations(), 4)) {
            case 1 -> new int[] {-rz, rx};
            case 2 -> new int[] {-rx, -rz};
            case 3 -> new int[] {rz, -rx};
            default -> new int[] {rx, rz};
        };
        return new boolean[] {rotated[0] == 1, rotated[1] == 1, rotated[0] == -1, rotated[1] == -1};
    }

    private Transforms() {
    }
}
