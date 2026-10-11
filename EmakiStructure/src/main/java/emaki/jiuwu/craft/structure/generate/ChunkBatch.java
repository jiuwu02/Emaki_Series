package emaki.jiuwu.craft.structure.generate;

import org.bukkit.Location;

public interface ChunkBatch {

    Location location();

    int blockCount();

    void writeToRegion();

    void markFinished();

    boolean finished();
}
