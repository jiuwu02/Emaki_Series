package emaki.jiuwu.craft.cooking.model;

import java.nio.file.Path;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public record StationCoordinates(String world, int x, int y, int z) {

    private static final Pattern WORLD_SEGMENT_PATTERN = Pattern.compile("[^a-zA-Z0-9._-]+");

    public StationCoordinates {
        world = Texts.toStringSafe(world).trim();
    }

    public static StationCoordinates fromBlock(Block block) {
        if (block == null || block.getWorld() == null) {
            return null;
        }
        return new StationCoordinates(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
    }

    public static StationCoordinates fromSection(YamlSection section) {
        if (section == null) {
            return null;
        }
        String world = section.getString("world", "");
        Integer x = section.getInt("x", null);
        Integer y = section.getInt("y", null);
        Integer z = section.getInt("z", null);
        if (Texts.isBlank(world) || x == null || y == null || z == null) {
            return null;
        }
        return new StationCoordinates(world, x, y, z);
    }

    public Block block() {
        World resolved = Bukkit.getWorld(world);
        return resolved == null ? null : resolved.getBlockAt(x, y, z);
    }

    public Location location(double offsetX, double offsetY, double offsetZ) {
        World resolved = Bukkit.getWorld(world);
        return resolved == null ? null : new Location(resolved, x + offsetX, y + offsetY, z + offsetZ);
    }

    public Path relativeDataPath() {
        return Path.of("data", "stations", sanitizedWorld(), x + "_" + y + "_" + z + ".yml");
    }

    public String runtimeKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private String sanitizedWorld() {
        String normalized = WORLD_SEGMENT_PATTERN.matcher(world).replaceAll("_");
        return normalized.isBlank() ? "world" : normalized;
    }
}
