package emaki.jiuwu.craft.corelib.web.preview;

public interface WebItemLayerPreviewProvider {

    String id();

    default int order() {
        return 100;
    }

    WebItemLayerPreviewResult preview(WebItemLayerPreviewRequest request);
}
