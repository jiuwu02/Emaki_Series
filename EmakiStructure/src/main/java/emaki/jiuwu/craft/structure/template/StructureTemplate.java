package emaki.jiuwu.craft.structure.template;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.block.data.BlockData;

import emaki.jiuwu.craft.structure.template.nbt.NbtValue;

public final class StructureTemplate {

    private static final long PER_BLOCK_ESTIMATE_BYTES = 40L;

    private final String id;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int[] paletteIndices;
    private final BlockData[] palette;
    private final Map<Long, NbtValue.CompoundTag> blockEntities;
    private final List<TemplateEntity> entities;
    private final long blockCount;
    private final int skippedPaletteEntries;
    private final long estimatedBytes;

    public StructureTemplate(
            String id,
            int sizeX,
            int sizeY,
            int sizeZ,
            int[] paletteIndices,
            BlockData[] palette,
            Map<Long, NbtValue.CompoundTag> blockEntities,
            List<TemplateEntity> entities,
            int skippedPaletteEntries) {
        this.id = Objects.requireNonNull(id, "id");
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.paletteIndices = paletteIndices.clone();
        this.palette = palette.clone();
        this.blockEntities = Map.copyOf(blockEntities);
        this.entities = List.copyOf(entities);
        this.skippedPaletteEntries = skippedPaletteEntries;
        long stored = 0;
        for (int index : this.paletteIndices) {
            if (index >= 0) {
                stored++;
            }
        }
        this.blockCount = stored;
        this.estimatedBytes = stored * PER_BLOCK_ESTIMATE_BYTES + entityPayloadBytes(this.entities);
    }

    public String id() {
        return id;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public long blockCount() {
        return blockCount;
    }

    public long volume() {
        return (long) sizeX * sizeY * sizeZ;
    }

    public int paletteIndexAt(int x, int y, int z) {
        if (!withinBounds(x, y, z)) {
            return -1;
        }
        return paletteIndices[flatIndex(x, y, z)];
    }

    public BlockData blockAt(int x, int y, int z) {
        int index = paletteIndexAt(x, y, z);
        return index < 0 ? null : palette[index];
    }

    public Optional<NbtValue.CompoundTag> blockEntityAt(int x, int y, int z) {
        if (!withinBounds(x, y, z)) {
            return Optional.empty();
        }
        return Optional.ofNullable(blockEntities.get(packPosition(x, y, z)));
    }

    public List<TemplateEntity> entities() {
        return entities;
    }

    public int skippedPaletteEntries() {
        return skippedPaletteEntries;
    }

    public long estimatedBytes() {
        return estimatedBytes;
    }

    static long packPosition(int x, int y, int z) {
        return ((long) y << 40) | ((long) z << 20) | (x & 0xFFFFFL);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof StructureTemplate that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id + " " + sizeX + "x" + sizeY + "x" + sizeZ;
    }

    private boolean withinBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    private int flatIndex(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    private static long entityPayloadBytes(List<TemplateEntity> entities) {
        long total = 0;
        for (TemplateEntity entity : entities) {
            total += payloadBytes(entity.nbt());
        }
        return total;
    }

    private static long payloadBytes(NbtValue value) {
        if (value == null) {
            return 0;
        }
        return switch (value) {
            case NbtValue.StringTag tag -> tag.value().length();
            case NbtValue.BytesTag tag -> tag.value().length;
            case NbtValue.IntsTag tag -> tag.value().length * 4L;
            case NbtValue.LongsTag tag -> tag.value().length * 8L;
            case NbtValue.ListTag list -> list.values().stream().mapToLong(StructureTemplate::payloadBytes).sum();
            case NbtValue.CompoundTag compound -> compound.entries().entrySet().stream()
                    .mapToLong(entry -> entry.getKey().length() + payloadBytes(entry.getValue()))
                    .sum();
            default -> 8L;
        };
    }
}
