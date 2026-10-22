package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public final class WorldFolders {

    private WorldFolders() {
    }

    public static Path dataDir(File worldContainer, String worldName) {
        String level = levelName(worldContainer);
        Path levelDir = new File(worldContainer, level).toPath();
        if (worldName.equals(level)) {
            return levelDir;
        }
        return levelDir.resolve("dimensions").resolve("minecraft").resolve(worldName);
    }

    public static List<Path> allDirs(File worldContainer, String worldName) {
        return List.of(dataDir(worldContainer, worldName));
    }

    public static boolean anyPresent(File worldContainer, String worldName) {
        return Files.isDirectory(dataDir(worldContainer, worldName));
    }

    public static List<Path> sweepRoots(File worldContainer) {
        Path levelDir = new File(worldContainer, levelName(worldContainer)).toPath();
        return List.of(levelDir.resolve("dimensions").resolve("minecraft"));
    }

    public static String levelName(File worldContainer) {
        File properties = new File(worldContainer, "server.properties");
        if (!properties.isFile()) {
            return "world";
        }
        Properties loaded = new Properties();
        try (FileInputStream stream = new FileInputStream(properties)) {
            loaded.load(stream);
        } catch (IOException exception) {
            return "world";
        }
        String value = loaded.getProperty("level-name", "world");
        if (value == null || value.isBlank()) {
            return "world";
        }
        return value.trim();
    }
}
