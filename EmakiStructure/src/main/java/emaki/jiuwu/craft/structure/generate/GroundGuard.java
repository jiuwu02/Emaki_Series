package emaki.jiuwu.craft.structure.generate;

import emaki.jiuwu.craft.structure.config.AppConfig;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class GroundGuard {

    private static final int FILL_DEPTH = 16;

    public enum Verdict {
        SUPPORTED, UNSUPPORTED, OUT_OF_WORLD
    }

    private GroundGuard() {
    }

    public static Verdict ensure(World world, int x, int surfaceY, int z, AppConfig.GroundMode mode) {
        if (surfaceY - 1 < world.getMinHeight()) {
            return Verdict.OUT_OF_WORLD;
        }
        if (!world.getBlockAt(x, surfaceY - 1, z).isPassable()) {
            return Verdict.SUPPORTED;
        }
        if (mode == AppConfig.GroundMode.KEEP) {
            return Verdict.SUPPORTED;
        }
        if (mode == AppConfig.GroundMode.SKIP) {
            return Verdict.UNSUPPORTED;
        }
        int depth = fillDepth(world, x, surfaceY, z);
        if (depth == 0) {
            return Verdict.UNSUPPORTED;
        }
        fillColumn(world, x, surfaceY, z, depth);
        return Verdict.SUPPORTED;
    }

    private static int fillDepth(World world, int x, int surfaceY, int z) {
        for (int offset = 1; offset <= FILL_DEPTH; offset++) {
            int probeY = surfaceY - 1 - offset;
            if (probeY < world.getMinHeight()) {
                return 0;
            }
            if (!world.getBlockAt(x, probeY, z).isPassable()) {
                return offset;
            }
        }
        return 0;
    }

    private static void fillColumn(World world, int x, int surfaceY, int z, int depth) {
        for (int offset = 0; offset < depth; offset++) {
            world.getBlockAt(x, surfaceY - 1 - offset, z).setType(Material.STONE, false);
        }
    }
}
