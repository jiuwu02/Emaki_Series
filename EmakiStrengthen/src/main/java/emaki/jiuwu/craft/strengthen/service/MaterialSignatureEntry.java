package emaki.jiuwu.craft.strengthen.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

record MaterialSignatureEntry(String materialId,
        String countKey,
        int amount) {

    MaterialSignatureEntry {
        materialId = normalize(materialId);
        countKey = normalize(countKey);
        amount = Math.max(0, amount);
    }

    Map<String, Object> row() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("material_id", materialId);
        result.put("count_key", countKey);
        result.put("amount", amount);
        return Map.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
