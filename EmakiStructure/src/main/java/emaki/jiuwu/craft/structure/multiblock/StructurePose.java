package emaki.jiuwu.craft.structure.multiblock;

import emaki.jiuwu.craft.structure.generate.StructureFacing;

public record StructurePose(String worldName,
                           MultiblockDefinition definition,
                           int anchorX,
                           int anchorY,
                           int anchorZ,
                           StructureFacing facing) {

    public String definitionId() {
        return definition.id();
    }

    public long chunkKey() {
        return ((long) (anchorX >> 4) << 32) | ((anchorZ >> 4) & 0xFFFFFFFFL);
    }
}
