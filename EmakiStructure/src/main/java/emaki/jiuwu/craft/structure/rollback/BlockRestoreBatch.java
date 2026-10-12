package emaki.jiuwu.craft.structure.rollback;

import emaki.jiuwu.craft.structure.generate.BlockEntityWriter;
import emaki.jiuwu.craft.structure.generate.ChunkBatch;
import emaki.jiuwu.craft.structure.template.nbt.NbtReader;
import emaki.jiuwu.craft.structure.template.nbt.NbtValue;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

public final class BlockRestoreBatch implements ChunkBatch {

    private final World world;
    private final Location location;
    private final List<GenerationJournal.BlockSnapshot> snapshots;
    private final BlockEntityWriter blockEntityWriter;
    private volatile boolean finished;
    private volatile int failures;

    public BlockRestoreBatch(World world, Location location,
            List<GenerationJournal.BlockSnapshot> snapshots, BlockEntityWriter blockEntityWriter) {
        this.world = world;
        this.location = location;
        this.snapshots = List.copyOf(snapshots);
        this.blockEntityWriter = blockEntityWriter;
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
        for (GenerationJournal.BlockSnapshot snapshot : snapshots) {
            Block block = world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
            try {
                block.setBlockData(Bukkit.createBlockData(snapshot.state()), false);
            } catch (IllegalArgumentException exception) {
                failed++;
                continue;
            }
            restoreContainer(snapshot);
        }
        if (failed > 0) {
            failures += failed;
        }
    }

    private void restoreContainer(GenerationJournal.BlockSnapshot snapshot) {
        byte[] payload = snapshot.containerPayload();
        if (payload == null || payload.length == 0) {
            return;
        }
        try {
            NbtValue.CompoundTag nbt = NbtReader.readRoot(payload).root();
            blockEntityWriter.apply(world, snapshot.x(), snapshot.y(), snapshot.z(), nbt);
        } catch (RuntimeException exception) {
            failures++;
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
