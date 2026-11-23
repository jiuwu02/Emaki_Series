package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;

public final class WorldFolders {

    private WorldFolders() {
    }

    public static Path dataDir(File worldContainer, String worldName) {
        World loaded = Bukkit.getWorld(worldName);
        if (loaded != null) {
            return loaded.getWorldFolder().toPath();
        }
        return siblingWorldDir(worldContainer, worldName).toPath();
    }

    public static List<Path> allDirs(File worldContainer, String worldName) {
        LinkedHashSet<Path> dirs = new LinkedHashSet<>();
        dirs.add(dataDir(worldContainer, worldName));
        dirs.add(new File(worldContainer, worldName).toPath());
        return List.copyOf(dirs);
    }

    public static boolean anyPresent(File worldContainer, String worldName) {
        for (Path dir : allDirs(worldContainer, worldName)) {
            if (Files.isDirectory(dir)) {
                return true;
            }
        }
        return false;
    }

    public static List<Path> sweepRoots(File worldContainer) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        File dimensionRoot = dimensionRoot(worldContainer);
        if (dimensionRoot != null) {
            roots.add(dimensionRoot.toPath());
        }
        roots.add(worldContainer.toPath());
        return List.copyOf(roots);
    }

    private static File dimensionRoot(File worldContainer) {
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        File mainFolder = main == null ? null : main.getWorldFolder();
        return mainFolder == null ? null : mainFolder.getParentFile();
    }

    private static File siblingWorldDir(File worldContainer, String worldName) {
        File dimensionRoot = dimensionRoot(worldContainer);
        return dimensionRoot == null
                ? new File(worldContainer, worldName)
                : new File(dimensionRoot, worldName);
    }
}
