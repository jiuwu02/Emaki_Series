package emaki.jiuwu.craft.structure.generate;

import emaki.jiuwu.craft.structure.rollback.GenerationJournal;
import emaki.jiuwu.craft.structure.template.StructureExporter;
import emaki.jiuwu.craft.structure.template.nbt.NbtWriter;
import emaki.jiuwu.craft.structure.template.nbt.NbtValue;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class RegionBatch implements ChunkBatch {

    public record Write(int x, int y, int z, BlockData data) {
    }

    private final World world;
    private final Location location;
    private final List<Write> writes;
    private final AtomicLong overwritten;
    private final GenerationJournal journal;
    private volatile boolean finished;

    public RegionBatch(World world, Location location, List<Write> writes, AtomicLong overwritten,
            GenerationJournal journal) {
        this.world = world;
        this.location = location;
        this.writes = writes;
        this.overwritten = overwritten;
        this.journal = journal;
    }

    @Override
    public Location location() {
        return location;
    }

    @Override
    public int blockCount() {
        return writes.size();
    }

    @Override
    public void writeToRegion() {
        captureSnapshot();
        for (Write write : writes) {
            Block block = world.getBlockAt(write.x(), write.y(), write.z());
            if (!block.getType().isAir()) {
                overwritten.incrementAndGet();
            }
            block.setBlockData(write.data(), false);
        }
    }

    private void captureSnapshot() {
        if (journal == null) {
            return;
        }
        String worldName = world.getName();
        List<GenerationJournal.BlockSnapshot> captured = new ArrayList<>();
        for (Write write : writes) {
            Block block = world.getBlockAt(write.x(), write.y(), write.z());
            if (block.getType().isAir()) {
                continue;
            }
            NbtValue.CompoundTag container = StructureExporter.readContainer(block);
            byte[] payload = container == null ? null : NbtWriter.write(container);
            captured.add(new GenerationJournal.BlockSnapshot(worldName,
                    write.x(), write.y(), write.z(),
                    block.getBlockData().getAsString(), payload));
        }
        journal.appendBlocks(captured);
    }

    @Override
    public void markFinished() {
        finished = true;
    }

    @Override
    public boolean finished() {
        return finished;
    }
}
