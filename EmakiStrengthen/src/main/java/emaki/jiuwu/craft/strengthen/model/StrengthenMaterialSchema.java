package emaki.jiuwu.craft.strengthen.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class StrengthenMaterialSchema {

    public static final int LEGACY_VERSION = 1;
    public static final int CANONICAL_VERSION = 2;

    private StrengthenMaterialSchema() {
    }

    public static Map<String, Object> canonicalize(Map<String, ?> source, int index) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(source);
        Object rawSources = copy.get("item_sources");
        if (rawSources == null && copy.containsKey("item")) {
            rawSources = copy.get("item");
            copy.put("item_sources", rawSources);
        }
        String materialId = text(copy.get("material_id"));
        if (materialId.isEmpty()) {
            materialId = text(copy.get("id"));
        }
        if (materialId.isEmpty()) {
            materialId = text(copy.get("count_key"));
        }
        if (materialId.isEmpty()) {
            materialId = singleSource(rawSources);
        }
        if (materialId.isEmpty() && sourceCount(rawSources) > 1) {
            return Map.of();
        }
        if (materialId.isEmpty()) {
            materialId = "material_" + (Math.max(0, index) + 1);
        }
        String countKey = text(copy.get("count_key"));
        copy.put("material_id", normalize(materialId));
        copy.put("count_key", countKey.isEmpty() ? normalize(materialId) : normalize(countKey));
        return Map.copyOf(copy);
    }

    private static int sourceCount(Object raw) {
        if (raw instanceof String text) {
            return text(text).isEmpty() ? 0 : 1;
        }
        if (raw instanceof Iterable<?> iterable) {
            int count = 0;
            for (Object entry : iterable) {
                if (!text(entry).isEmpty()) {
                    count++;
                }
            }
            return count;
        }
        return 0;
    }

    private static String singleSource(Object raw) {
        if (raw instanceof String text) {
            return text;
        }
        if (raw instanceof Iterable<?> iterable) {
            String only = "";
            for (Object entry : iterable) {
                String token = text(entry);
                if (token.isEmpty()) {
                    continue;
                }
                if (!only.isEmpty()) {
                    return "";
                }
                only = token;
            }
            return only;
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String normalize(String value) {
        return text(value).toLowerCase(Locale.ROOT);
    }
}
