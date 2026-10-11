package emaki.jiuwu.craft.structure.template;

import emaki.jiuwu.craft.structure.template.nbt.NbtIo;
import emaki.jiuwu.craft.structure.template.nbt.NbtReader;

public record ImportLimits(
        long maxFileBytes,
        long maxOutputBytes,
        int maxDepth,
        int maxSizeX,
        int maxSizeY,
        int maxSizeZ,
        long maxTotalBlocks,
        int paletteLimit,
        long blockEntityMaxBytes,
        LegacyPolicy legacyPolicy) {

    public enum LegacyPolicy {

        REJECT,
        SKIP
    }

    public static ImportLimits defaults() {
        return new ImportLimits(
                32L * 1024 * 1024,
                NbtIo.DEFAULT_MAX_OUTPUT_BYTES,
                NbtReader.DEFAULT_MAX_DEPTH,
                512,
                512,
                512,
                128L * 128 * 128,
                4096,
                64L * 1024,
                LegacyPolicy.REJECT);
    }
}
