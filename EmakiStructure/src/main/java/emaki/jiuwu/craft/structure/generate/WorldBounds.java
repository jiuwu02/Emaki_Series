package emaki.jiuwu.craft.structure.generate;

import org.bukkit.World;

public final class WorldBounds {

    private static final int HORIZONTAL_LIMIT = 29999984;

    private WorldBounds() {
    }

    public static boolean contains(World world, int x, int y, int z) {
        return y >= world.getMinHeight()
                && y < world.getMaxHeight()
                && Math.abs(x) <= HORIZONTAL_LIMIT
                && Math.abs(z) <= HORIZONTAL_LIMIT;
    }

    public static String describe(World world, int x, int y, int z) {
        return "x=" + x + " y=" + y + " z=" + z
                + " (允许 y " + world.getMinHeight() + ".." + (world.getMaxHeight() - 1)
                + "，水平 ±" + HORIZONTAL_LIMIT + ")";
    }
}
