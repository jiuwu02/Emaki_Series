package emaki.jiuwu.craft.station.queue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LegacyQueueIdentityMigration {

    private LegacyQueueIdentityMigration() {
    }

    public static Map<String, Object> migrate(Map<String, ?> legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return Map.of();
        }
        String recipe = text(legacy.get("recipe"));
        if (recipe.isBlank()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(legacy);
        result.put("schema", 2);
        result.put("recipe_identity", first(legacy.get("recipe_identity"), recipe));
        List<Map<String, Object>> consumed = new ArrayList<>();
        Object rawConsumed = legacy.get("consumed");
        if (rawConsumed instanceof Iterable<?> values) {
            for (Object raw : values) {
                if (!(raw instanceof Map<?, ?> map)) {
                    return Map.of();
                }
                Map<String, Object> entry = normalize(map);
                String source = first(entry.get("matched_source"), entry.get("source"));
                if (source.isBlank()) {
                    return Map.of();
                }
                String materialId = identity(entry.get("material_id"), source);
                String countKey = identity(entry.get("count_key"), materialId);
                String requirementId = identity(entry.get("requirement_id"), materialId);
                entry.put("material_id", materialId);
                entry.put("count_key", countKey);
                entry.put("requirement_id", requirementId);
                entry.put("matched_source", source);
                consumed.add(entry);
            }
        }
        if (!consumed.isEmpty()) {
            result.put("consumed", consumed);
        }
        return Map.copyOf(result);
    }

    private static Map<String, Object> normalize(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static String identity(Object value, Object fallback) {
        String text = text(value);
        return text.isBlank() || text.equalsIgnoreCase("legacy") ? text(fallback) : text;
    }

    private static String first(Object value, Object fallback) {
        String text = text(value);
        return text.isBlank() ? text(fallback) : text;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
