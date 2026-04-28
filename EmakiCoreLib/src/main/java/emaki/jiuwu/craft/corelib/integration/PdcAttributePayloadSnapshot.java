package emaki.jiuwu.craft.corelib.integration;

import java.util.Map;

public record PdcAttributePayloadSnapshot(String sourceId,
        Map<String, Double> attributes,
        Map<String, String> meta) {

    public PdcAttributePayloadSnapshot {
        sourceId = sourceId == null ? "" : sourceId;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        meta = meta == null ? Map.of() : Map.copyOf(meta);
    }
}
