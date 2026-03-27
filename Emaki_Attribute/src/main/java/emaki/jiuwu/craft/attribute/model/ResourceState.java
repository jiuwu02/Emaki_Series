package emaki.jiuwu.craft.attribute.model;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import java.util.LinkedHashMap;
import java.util.Map;

public record ResourceState(String resourceId,
                            double defaultMax,
                            double bonusMax,
                            double currentMax,
                            double currentValue,
                            String sourceSignature,
                            int schemaVersion) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ResourceState {
        sourceSignature = sourceSignature == null ? "" : sourceSignature;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("resource_id", resourceId);
        map.put("default_max", defaultMax);
        map.put("bonus_max", bonusMax);
        map.put("current_max", currentMax);
        map.put("current_value", currentValue);
        map.put("source_signature", sourceSignature);
        map.put("schema_version", schemaVersion);
        return map;
    }

    public static ResourceState fromMap(Object raw) {
        if (raw == null) {
            return null;
        }
        return new ResourceState(
            ConfigNodes.string(raw, "resource_id", null),
            Numbers.tryParseDouble(ConfigNodes.get(raw, "default_max"), 0D),
            Numbers.tryParseDouble(ConfigNodes.get(raw, "bonus_max"), 0D),
            Numbers.tryParseDouble(ConfigNodes.get(raw, "current_max"), 0D),
            Numbers.tryParseDouble(ConfigNodes.get(raw, "current_value"), 0D),
            ConfigNodes.string(raw, "source_signature", ""),
            Numbers.tryParseInt(ConfigNodes.get(raw, "schema_version"), CURRENT_SCHEMA_VERSION)
        );
    }
}
