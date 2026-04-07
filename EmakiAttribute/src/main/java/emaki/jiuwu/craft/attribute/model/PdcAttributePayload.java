package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record PdcAttributePayload(String sourceId,
        Map<String, Double> attributes,
        Map<String, String> meta,
        int schemaVersion,
        long updatedAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PdcAttributePayload {
        sourceId = normalizeId(sourceId);
        attributes = normalizeAttributes(attributes);
        meta = normalizeMeta(meta);
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        updatedAt = updatedAt <= 0L ? System.currentTimeMillis() : updatedAt;
    }

    public static PdcAttributePayload of(String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        return new PdcAttributePayload(sourceId, attributes, meta, CURRENT_SCHEMA_VERSION, System.currentTimeMillis());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source_id", sourceId);
        map.put("schema_version", schemaVersion);
        map.put("updated_at", updatedAt);
        map.put("attributes", new LinkedHashMap<>(attributes));
        map.put("meta", new LinkedHashMap<>(meta));
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
            attributes.put(normalizeId(entry.getKey()), value);
        }
        Map<String, String> meta = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(map.get("meta")).entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            meta.put(normalizeId(entry.getKey()), Texts.toStringSafe(entry.getValue()));
        }
        return new PdcAttributePayload(
                ConfigNodes.string(map, "source_id", ""),
                attributes,
                meta,
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
            normalized.put(normalizeId(entry.getKey()), entry.getValue());
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
            normalized.put(normalizeId(entry.getKey()), entry.getValue());
        }
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
