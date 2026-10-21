package emaki.jiuwu.craft.dungeon.world;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public interface WorldLayout {

    Path dataDir(File worldContainer, String worldName);

    List<Path> sweepRoots(File worldContainer);
}
