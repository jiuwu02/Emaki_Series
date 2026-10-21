package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class WorldFolders {

    private WorldFolders() {
    }

    public static List<Path> dataCandidates(File worldContainer, String worldName) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(new File(worldContainer, worldName).toPath());
        String levelName = levelName(worldContainer);
        if (levelName != null) {
            candidates.add(new File(worldContainer, levelName).toPath()
                    .resolve("dimensions").resolve("minecraft").resolve(worldName));
        }
        return candidates;
    }

    public static boolean anyPresent(File worldContainer, String worldName) {
        for (Path candidate : dataCandidates(worldContainer, worldName)) {
            if (java.nio.file.Files.isDirectory(candidate)) {
                return true;
            }
        }
        return false;
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
