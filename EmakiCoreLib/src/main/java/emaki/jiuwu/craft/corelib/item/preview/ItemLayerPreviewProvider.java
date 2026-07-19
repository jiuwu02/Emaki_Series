package emaki.jiuwu.craft.corelib.item.preview;

public interface ItemLayerPreviewProvider {

    String id();

    default int order() {
        return 100;
    }

    ItemLayerPreviewResult preview(ItemLayerPreviewRequest request);
}
