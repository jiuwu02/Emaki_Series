package emaki.jiuwu.craft.corelib.assembly;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.pdc.SnapshotCodec;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class EmakiItemLayerCodecRegistry {

    private final Map<String, SnapshotCodec<EmakiItemLayerSnapshot>> codecs = new LinkedHashMap<>();

    public void register(String namespaceId, SnapshotCodec<EmakiItemLayerSnapshot> codec) {
        if (Texts.isBlank(namespaceId) || codec == null) {
            return;
        }
        codecs.put(normalizeId(namespaceId), codec);
    }

    public void unregister(String namespaceId) {
        if (Texts.isBlank(namespaceId)) {
            return;
        }
        codecs.remove(normalizeId(namespaceId));
    }

    public SnapshotCodec<EmakiItemLayerSnapshot> codecFor(String namespaceId) {
        SnapshotCodec<EmakiItemLayerSnapshot> codec = Texts.isBlank(namespaceId) ? null : codecs.get(normalizeId(namespaceId));
        return codec == null ? EmakiItemLayerSnapshot.CODEC : codec;
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
