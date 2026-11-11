package emaki.jiuwu.craft.dungeon.world;

import java.io.File;

import org.bukkit.Bukkit;

public final class WorldNames {

    public static final String PREFIX = "emd_dungeon_";

    private static final int MAX_SEQUENCE = 512;

    private final File worldContainer;

    public WorldNames(File worldContainer) {
        this.worldContainer = worldContainer;
    }

    public String next(String dungeonId) {
        String key = dungeonId == null ? "" : dungeonId;
        for (int sequence = 1; sequence <= MAX_SEQUENCE; sequence++) {
            String candidate = PREFIX + key + "_" + sequence;
            if (!inUse(candidate)) {
                return candidate;
            }
        }
        return PREFIX + key + "_" + System.currentTimeMillis();
    }

    private boolean inUse(String worldName) {
        if (Bukkit.getWorld(worldName) != null) {
            return true;
        }
        return WorldFolders.anyPresent(worldContainer, worldName);
    }

    public static boolean isInstanceWorld(String worldName) {
        return worldName != null && worldName.startsWith(PREFIX);
    }
}
