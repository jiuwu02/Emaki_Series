package emaki.jiuwu.craft.structure.api.model;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of a loaded structure template's identity and size.
 *
 * <p>The view carries no world or {@code BlockData} references, so instances are safe to
 * hold and to read from any thread. Coordinates are template-local: {@code (0,0,0)} is the
 * minimum corner of the template bounding box and every component is in
 * {@code [0, sizeX)} / {@code [0, sizeY)} / {@code [0, sizeZ)}.
 *
 * @param id         the template id, matching its file name without extension
 * @param sizeX      template width along the world X axis before rotation
 * @param sizeY      template height along the world Y axis
 * @param sizeZ      template depth along the world Z axis before rotation
 * @param blockCount number of block entries stored in the template, air included
 */
public record StructureTemplateView(@NotNull String id,
                                    int sizeX,
                                    int sizeY,
                                    int sizeZ,
                                    int blockCount) {

    /**
     * Creates a view.
     *
     * @param id         the template id
     * @param sizeX      template width
     * @param sizeY      template height
     * @param sizeZ      template depth
     * @param blockCount stored block entry count
     * @throws NullPointerException     when {@code id} is {@code null}
     * @throws IllegalArgumentException when any size is not positive or {@code blockCount} is negative
     */
    public StructureTemplateView {
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("size must be positive: " + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        if (blockCount < 0) {
            throw new IllegalArgumentException("blockCount must not be negative: " + blockCount);
        }
    }
}
