package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class WorldFolders {

    private WorldFolders() {
    }

    public static Path dataDir(File worldContainer, String worldName) {
        return new File(worldContainer, worldName).toPath();
    }

    public static List<Path> allDirs(File worldContainer, String worldName) {
        return List.of(dataDir(worldContainer, worldName));
    }

    public static boolean anyPresent(File worldContainer, String worldName) {
        return Files.isDirectory(dataDir(worldContainer, worldName));
    }

    public static List<Path> sweepRoots(File worldContainer) {
        return List.of(worldContainer.toPath());
    }
}
