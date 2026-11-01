package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.Map;

final class ForgeAllocationMath {
    private ForgeAllocationMath() {
    }

    static Map<String, Integer> requiredConsumption(Map<String, Integer> units) {
        return new LinkedHashMap<>(units == null ? Map.of() : units);
    }

    static Map<String, Integer> optionalConsumption(Map<String, Integer> units,
            Map<String, Integer> placed) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (units == null) {
            return result;
        }
        for (Map.Entry<String, Integer> entry : units.entrySet()) {
            int unit = Math.max(1, entry.getValue());
            int total = placed == null ? 0 : Math.max(0, placed.getOrDefault(entry.getKey(), 0));
            result.put(entry.getKey(), (total / unit) * unit);
        }
        return result;
    }

    static Map<String, Integer> aggregate(Map<String, Integer> amountsByIdentity,
            Map<String, String> countKeysByIdentity) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (amountsByIdentity == null || countKeysByIdentity == null) {
            return result;
        }
        for (Map.Entry<String, Integer> entry : amountsByIdentity.entrySet()) {
            String countKey = countKeysByIdentity.get(entry.getKey());
            if (countKey != null && !countKey.isBlank()) {
                result.merge(countKey, Math.max(0, entry.getValue()), Integer::sum);
            }
        }
        return result;
    }
}
