package emaki.jiuwu.craft.attribute.model;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record AttributeSnapshot(int schemaVersion,
                                String sourceSignature,
                                Map<String, Double> values,
                                long updatedAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public AttributeSnapshot {
        sourceSignature = sourceSignature == null ? "" : sourceSignature;
        values = values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
    }

    public static AttributeSnapshot empty(String sourceSignature) {
        return new AttributeSnapshot(CURRENT_SCHEMA_VERSION, sourceSignature, Map.of(), System.currentTimeMillis());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schema_version", schemaVersion);
        map.put("source_signature", sourceSignature);
        map.put("updated_at", updatedAt);
        map.put("values", new LinkedHashMap<>(values));
        return map;
    }

    public static AttributeSnapshot fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Map<String, Double> values = new LinkedHashMap<>();
        Object valuesRaw = map.get("values");
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(valuesRaw).entrySet()) {
            values.put(entry.getKey().toLowerCase(Locale.ROOT), Numbers.tryParseDouble(entry.getValue(), 0D));
        }
        return new AttributeSnapshot(
            Numbers.tryParseInt(map.get("schema_version"), CURRENT_SCHEMA_VERSION),
            ConfigNodes.string(map, "source_signature", ""),
            values,
            Numbers.tryParseLong(map.get("updated_at"), System.currentTimeMillis())
        );
    }
}
