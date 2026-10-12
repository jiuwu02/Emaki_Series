package emaki.jiuwu.craft.structure.integration.furniture;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;

public interface FurnitureRestorer {

    Entity place(String rawId, Location location);

    boolean remove(Entity entity);

    static BlockFace faceOf(double yaw) {
        double normalized = ((yaw % 360.0d) + 360.0d) % 360.0d;
        int quadrant = (int) Math.floor(normalized / 90.0d + 0.5d) % 4;
        return switch (quadrant) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }
}
