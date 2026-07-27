package emaki.jiuwu.craft.item.api.preview;

/**
 * Supplies one optional transformation layer for EmakiItem previews.
 */
public interface ItemLayerPreviewProvider {

    /** {@return the stable layer identifier} */
    String id();

    /**
     * Returns the layer order. Lower values are applied first.
     *
     * @return the layer order
     */
    default int order() {
        return 100;
    }

    /**
     * Builds a preview for this layer.
     *
     * @param request the defensive preview request snapshot
     * @return the preview result
     */
    ItemLayerPreviewResult preview(ItemLayerPreviewRequest request);
}
