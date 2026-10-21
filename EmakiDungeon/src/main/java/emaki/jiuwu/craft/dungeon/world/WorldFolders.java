package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.bukkit.Bukkit;

public final class WorldFolders {

    private static final WorldLayout LEGACY = new LegacyWorldLayout();
    private static final WorldLayout UNIFIED = new UnifiedWorldLayout();
    private static volatile WorldLayout current;

    private WorldFolders() {
    }

    public static Path dataDir(File worldContainer, String worldName) {
        return currentLayout().dataDir(worldContainer, worldName);
    }

    public static List<Path> allDirs(File worldContainer, String worldName) {
        List<Path> dirs = new ArrayList<>();
        for (WorldLayout layout : orderedLayouts()) {
            Path dir = layout.dataDir(worldContainer, worldName);
            if (!dirs.contains(dir)) {
                dirs.add(dir);
            }
        }
        return dirs;
    }

    public static List<Path> foreignDirs(File worldContainer, String worldName) {
        Path primary = dataDir(worldContainer, worldName);
        List<Path> dirs = new ArrayList<>();
        for (Path dir : allDirs(worldContainer, worldName)) {
            if (!dir.equals(primary)) {
                dirs.add(dir);
            }
        }
        return dirs;
    }

    public static boolean anyPresent(File worldContainer, String worldName) {
        return Files.isDirectory(dataDir(worldContainer, worldName));
    }

    public static List<Path> sweepRoots(File worldContainer) {
        List<Path> roots = new ArrayList<>();
        for (WorldLayout layout : orderedLayouts()) {
            for (Path root : layout.sweepRoots(worldContainer)) {
                if (!roots.contains(root)) {
                    roots.add(root);
                }
            }
        }
        return roots;
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

    private static WorldLayout currentLayout() {
        WorldLayout layout = current;
        if (layout == null) {
            layout = unifiedVersion(Bukkit.getMinecraftVersion()) ? UNIFIED : LEGACY;
            current = layout;
        }
        return layout;
    }

    private static List<WorldLayout> orderedLayouts() {
        WorldLayout currentLayout = currentLayout();
        WorldLayout other = currentLayout == LEGACY ? UNIFIED : LEGACY;
        return List.of(currentLayout, other);
    }

    private static boolean unifiedVersion(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        int dot = version.indexOf('.');
        String head = dot < 0 ? version : version.substring(0, dot);
        try {
            return Integer.parseInt(head) >= 26;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
