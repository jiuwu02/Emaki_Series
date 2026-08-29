package emaki.jiuwu.craft.attribute.api.model;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Immutable attribute payload stored on an item under a single source id.
 *
 * <p>Holds the attribute id/value pairs granted by the source, free-form string
 * metadata, optional per-attribute activation conditions, a schema version and
 * a last-updated timestamp. Maps are normalized and defensively copied; non-positive schema versions and
 * timestamps select current defaults. Use
 * {@link emaki.jiuwu.craft.attribute.api.PdcAttributeAccess} to persist and read these payloads.
 *
 * @param sourceId      the owning source id (normalized)
 * @param attributes    attribute id to value mapping (normalized keys)
 * @param meta          arbitrary string metadata (normalized keys)
 * @param conditions    optional per-attribute activation conditions
 * @param schemaVersion the payload schema version
 * @param updatedAt     epoch millis of the last update
 */
public record PdcAttributePayload(String sourceId,
        Map<String, Double> attributes,
        Map<String, String> meta,
        Map<String, String> conditions,
        int schemaVersion,
        long updatedAt) {

    /** Current payload schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 2;

    /**
     * Canonical constructor; normalizes ids, defaults the schema version and
     * stamps {@code updatedAt} when not supplied.
     */
    public PdcAttributePayload {
        sourceId = AttributeApiValues.normalizeId(sourceId);
        attributes = normalizeAttributes(attributes);
        meta = normalizeMeta(meta);
        conditions = normalizeMeta(conditions);
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        updatedAt = updatedAt <= 0L ? System.currentTimeMillis() : updatedAt;
    }

    /**
     * Convenience constructor without explicit conditions.
     *
     * @param sourceId      the owning source id
     * @param attributes    attribute id to value mapping
     * @param meta          string metadata
     * @param schemaVersion the schema version
     * @param updatedAt     epoch millis of the last update
     */
    public PdcAttributePayload(String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta,
            int schemaVersion,
            long updatedAt) {
        this(sourceId, attributes, meta, Map.of(), schemaVersion, updatedAt);
    }

    /**
     * Builds a payload with the current schema version and timestamp.
     *
     * @param sourceId   the owning source id
     * @param attributes attribute id to value mapping
     * @param meta       string metadata
     * @return the new payload
     */
    public static PdcAttributePayload of(String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        return new PdcAttributePayload(sourceId, attributes, meta, Map.of(), CURRENT_SCHEMA_VERSION, System.currentTimeMillis());
    }

    /**
     * Builds a payload including activation conditions, with the current schema
     * version and timestamp.
     *
     * @param sourceId   the owning source id
     * @param attributes attribute id to value mapping
     * @param meta       string metadata
     * @param conditions per-attribute activation conditions
     * @return the new payload
     */
    public static PdcAttributePayload of(String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta,
            Map<String, String> conditions) {
        return new PdcAttributePayload(sourceId, attributes, meta, conditions, CURRENT_SCHEMA_VERSION, System.currentTimeMillis());
    }

    /**
     * {@return the activation condition for an attribute, or {@code null}}
     *
     * @param attributeId the attribute id (normalized before lookup)
     */
    public String conditionFor(String attributeId) {
        if (conditions == null || conditions.isEmpty() || attributeId == null) {
            return null;
        }
        return conditions.get(AttributeApiValues.normalizeId(attributeId));
    }

    /** {@return whether this payload's values scale with item durability} */
    public boolean hasDurabilityScaling() {
        return "true".equalsIgnoreCase(meta.get("durability_scaling"));
    }

    /** {@return this payload serialized to a plain, persistable map} */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source_id", sourceId);
        map.put("schema_version", schemaVersion);
        map.put("updated_at", updatedAt);
        map.put("attributes", new LinkedHashMap<>(attributes));
        map.put("meta", new LinkedHashMap<>(meta));
        if (conditions != null && !conditions.isEmpty()) {
            map.put("conditions", new LinkedHashMap<>(conditions));
        }
        return map;
    }

    /**
     * Reconstructs a payload from a map produced by {@link #toMap()}.
     *
     * @param map the serialized form; may be {@code null}
     * @return the reconstructed payload, or {@code null} if {@code map} is
     *         {@code null}
     */
    public static PdcAttributePayload fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Map<String, Double> attributes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : AttributeApiValues.entries(map.get("attributes")).entrySet()) {
            Double value = AttributeApiValues.tryParseDouble(entry.getValue(), null);
            if (value == null) {
                continue;
            }
            attributes.put(AttributeApiValues.normalizeId(entry.getKey()), value);
        }
        Map<String, String> meta = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : AttributeApiValues.entries(map.get("meta")).entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            meta.put(AttributeApiValues.normalizeId(entry.getKey()), AttributeApiValues.toStringSafe(entry.getValue()));
        }
        Map<String, String> conditions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : AttributeApiValues.entries(map.get("conditions")).entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            conditions.put(AttributeApiValues.normalizeId(entry.getKey()), AttributeApiValues.toStringSafe(entry.getValue()));
        }
        return new PdcAttributePayload(
                AttributeApiValues.string(map, "source_id", ""),
                attributes,
                meta,
                conditions,
                AttributeApiValues.tryParseInt(map.get("schema_version"), CURRENT_SCHEMA_VERSION),
                AttributeApiValues.tryParseLong(map.get("updated_at"), System.currentTimeMillis())
        );
    }

    private static Map<String, Double> normalizeAttributes(Map<String, Double> attributes) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        if (attributes == null) {
            return Map.of();
        }
        for (Map.Entry<String, Double> entry : attributes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(AttributeApiValues.normalizeId(entry.getKey()), entry.getValue());
        }
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private static Map<String, String> normalizeMeta(Map<String, String> meta) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (meta == null) {
            return Map.of();
        }
        for (Map.Entry<String, String> entry : meta.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(AttributeApiValues.normalizeId(entry.getKey()), entry.getValue());
        }
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }
}
