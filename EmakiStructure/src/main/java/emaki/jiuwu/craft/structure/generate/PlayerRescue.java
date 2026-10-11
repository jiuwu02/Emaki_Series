package emaki.jiuwu.craft.structure.generate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class PlayerRescue {

    private static final int MAX_LIFT = 4;

    private PlayerRescue() {
    }

    public static List<Player> liftTrapped(World world, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ) {
        List<Player> trapped = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            Location feet = player.getLocation();
            int x = feet.getBlockX();
            int y = feet.getBlockY();
            int z = feet.getBlockZ();
            if (!inside(minX, minY, minZ, maxX, maxY, maxZ, x, y, z)) {
                continue;
            }
            if (isStandable(world, x, y, z)) {
                continue;
            }
            if (!lift(player, world, x, y, z)) {
                trapped.add(player);
            }
        }
        return trapped;
    }

    private static boolean lift(Player player, World world, int x, int y, int z) {
        for (int offset = 1; offset <= MAX_LIFT; offset++) {
            int targetY = y + offset;
            if (targetY + 1 >= world.getMaxHeight()) {
                return false;
            }
            if (isStandable(world, x, targetY, z)) {
                Location destination = player.getLocation();
                destination.setY(targetY + 1.0d);
                player.teleport(destination);
                return true;
            }
        }
        return false;
    }

    private static boolean isStandable(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        return feet.isPassable() && head.isPassable() && !ground.isPassable();
    }

    private static boolean inside(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
