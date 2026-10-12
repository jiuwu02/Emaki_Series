package emaki.jiuwu.craft.structure.rollback;

import emaki.jiuwu.craft.structure.generate.ChunkBatch;
import emaki.jiuwu.craft.structure.integration.furniture.FurniturePlacer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.List;

public final class EntityRestoreBatch implements ChunkBatch {

    private final World world;
    private final Location location;
    private final List<GenerationJournal.EntitySnapshot> snapshots;
    private final FurniturePlacer furniturePlacer;
    private volatile boolean finished;
    private volatile int failures;

    public EntityRestoreBatch(World world, Location location,
            List<GenerationJournal.EntitySnapshot> snapshots, FurniturePlacer furniturePlacer) {
        this.world = world;
        this.location = location;
        this.snapshots = List.copyOf(snapshots);
        this.furniturePlacer = furniturePlacer;
    }

    @Override
    public Location location() {
        return location;
    }

    @Override
    public int blockCount() {
        return snapshots.size();
    }

    @Override
    public void writeToRegion() {
        int failed = 0;
        for (GenerationJournal.EntitySnapshot snapshot : snapshots) {
            try {
                Entity entity = world.getEntity(snapshot.id());
                if (entity == null || entity.isDead()) {
                    continue;
                }
                boolean sourceRemoved = furniturePlacer != null && furniturePlacer.remove(entity);
                if (!sourceRemoved) {
                    entity.remove();
                }
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        if (failed > 0) {
            failures += failed;
        }
    }

    @Override
    public void markFinished() {
        finished = true;
    }

    @Override
    public boolean finished() {
        return finished;
    }

    public int failures() {
        return failures;
    }
}
