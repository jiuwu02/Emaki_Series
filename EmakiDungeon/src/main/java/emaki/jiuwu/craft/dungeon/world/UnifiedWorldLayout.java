package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public final class UnifiedWorldLayout implements WorldLayout {

    @Override
    public Path dataDir(File worldContainer, String worldName) {
        Path levelDir = new File(worldContainer, WorldFolders.levelName(worldContainer)).toPath();
        if (worldName.equals(WorldFolders.levelName(worldContainer))) {
            return levelDir;
        }
        return levelDir.resolve("dimensions").resolve("minecraft").resolve(worldName);
    }

    @Override
    public List<Path> sweepRoots(File worldContainer) {
        Path levelDir = new File(worldContainer, WorldFolders.levelName(worldContainer)).toPath();
        return List.of(levelDir.resolve("dimensions").resolve("minecraft"));
    }
}
