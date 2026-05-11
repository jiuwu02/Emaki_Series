package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record PdcAttributePayload(String sourceId,
        Map<String, Double> attributes,
        Map<String, String> meta,
        Map<String, String> conditions,
        int schemaVersion,
        long updatedAt) {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    public PdcAttributePayload {
        sourceId = Texts.normalizeId(sourceId);
        attributes = normalizeAttributes(attributes);
        meta = normalizeMeta(meta);
        conditions = normalizeMeta(conditions);
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        updatedAt = updatedAt <= 0L ? System.currentTimeMillis() : updatedAt;
    }

    /**
     * Backward-compatible constructor without conditions.
     */
    public PdcAttributePayload(String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta,
            int schemaVersion,
            long updatedAt) {
        this(sourceId, attributes, meta, Map.of(), schemaVersion, updatedAt);
    }

    public static PdcAttributePayload of(String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        return new PdcAttributePayload(sourceId, attributes, meta, Map.of(), CURRENT_SCHEMA_VERSION, System.currentTimeMillis());
    }

    public static PdcAttributePayload of(String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta,
            Map<String, String> conditions) {
        return new PdcAttributePayload(sourceId, attributes, meta, conditions, CURRENT_SCHEMA_VERSION, System.currentTimeMillis());
    }

    /**
     * Returns the condition expression for the given attribute id, or null if unconditional.
     */
    public String conditionFor(String attributeId) {
        if (conditions == null || conditions.isEmpty() || attributeId == null) {
            return null;
        }
        return conditions.get(Texts.normalizeId(attributeId));
    }

    /**
     * Returns true if this payload has durability scaling enabled.
     * When enabled, attribute values should be multiplied by the item's current durability percentage.
     */
    public boolean hasDurabilityScaling() {
        return "true".equalsIgnoreCase(meta.get("durability_scaling"));
    }

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

    public static PdcAttributePayload fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Map<String, Double> attributes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(map.get("attributes")).entrySet()) {
            Double value = Numbers.tryParseDouble(entry.getValue(), null);
            if (value == null) {
                continue;
            }
            attributes.put(Texts.normalizeId(entry.getKey()), value);
        }
        Map<String, String> meta = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(map.get("meta")).entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            meta.put(Texts.normalizeId(entry.getKey()), Texts.toStringSafe(entry.getValue()));
        }
        Map<String, String> conditions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(map.get("conditions")).entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            conditions.put(Texts.normalizeId(entry.getKey()), Texts.toStringSafe(entry.getValue()));
        }
        return new PdcAttributePayload(
                ConfigNodes.string(map, "source_id", ""),
                attributes,
                meta,
                conditions,
                Numbers.tryParseInt(map.get("schema_version"), CURRENT_SCHEMA_VERSION),
                Numbers.tryParseLong(map.get("updated_at"), System.currentTimeMillis())
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
            normalized.put(Texts.normalizeId(entry.getKey()), entry.getValue());
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
            normalized.put(Texts.normalizeId(entry.getKey()), entry.getValue());
        }
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }
}

