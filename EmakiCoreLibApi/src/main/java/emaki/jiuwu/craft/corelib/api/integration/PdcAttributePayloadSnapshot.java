package emaki.jiuwu.craft.corelib.api.integration;

import java.util.Map;

/**
 * Legacy CoreLib mirror of an EmakiAttribute PDC payload.
 *
 * <p>Carries only attributes and meta; conditions, schema version and update
 * timestamp are not represented and are lost when a full payload is projected
 * into this snapshot.
 *
 * @deprecated Superseded by {@code emaki.jiuwu.craft.attribute.model.PdcAttributePayload}
 *             in EmakiAttributeApi, which round-trips every field. Retained for
 *             one synchronized release window.
 */
@Deprecated(forRemoval = true)
public record PdcAttributePayloadSnapshot(String sourceId,
        Map<String, Double> attributes,
        Map<String, String> meta) {

    public PdcAttributePayloadSnapshot {
        sourceId = sourceId == null ? "" : sourceId;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        meta = meta == null ? Map.of() : Map.copyOf(meta);
    }
}
