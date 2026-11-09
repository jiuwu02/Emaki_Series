package emaki.jiuwu.craft.strengthen.api.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StrengthenRecipeSupport {

    static void mergeRaw(Map<String, Object> target, Map<String, Object> source) {
        if (target == null || source == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = StrengthenApiValues.lower(entry.getKey());
            Object existing = target.get(key);
            Object incoming = entry.getValue();
            Double existingNum = toDouble(existing);
            Double incomingNum = toDouble(incoming);
            if (existingNum != null && incomingNum != null) {
                target.put(key, existingNum + incomingNum);
            } else {
                target.put(key, incoming);
            }
        }
}

    static double resolveNumericValue(Object raw, Map<String, ?> variables) {
        return StrengthenApiValues.evaluateNumber(raw, variables);
}

    static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            return StrengthenApiValues.tryParseDouble(text, null);
        }
        return null;
}
}
