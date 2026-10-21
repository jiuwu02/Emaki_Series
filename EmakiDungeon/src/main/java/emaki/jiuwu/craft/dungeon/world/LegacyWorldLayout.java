package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public final class LegacyWorldLayout implements WorldLayout {

    @Override
    public Path dataDir(File worldContainer, String worldName) {
        return new File(worldContainer, worldName).toPath();
    }

    @Override
    public List<Path> sweepRoots(File worldContainer) {
        return List.of(worldContainer.toPath());
    }
}
