package emaki.jiuwu.craft.attribute.api.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Package-local value helpers shared by the attribute API's mapping and reading code.
 *
 * <p>The methods coerce arbitrary objects without leaking {@code null}: string conversion turns a
 * {@code null} into an empty string, mapped lookups fall back to a supplied default, ids are
 * normalized with {@link Locale#ROOT} lower-casing and space-to-underscore substitution, and the
 * {@code tryParse*} helpers accept either a {@link Number} or a numeric string and return the
 * supplied default when the input is null or unparsable instead of throwing. {@code entries}
 * flattens a mapping into string keys while skipping null keys, and {@code toPlainData}
 * recursively converts nested maps and collections into plain maps and lists, passing anything
 * else through unchanged.
 *
 * <p>Every member is package-private and consumed inside this module only.
 */
final class AttributeApiValues {

    private AttributeApiValues() {
    }

    static Map<String, Object> entries(Object mapping) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!(mapping instanceof Map<?, ?> map)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    static String string(Object mapping, String key, String defaultValue) {
        Object value = get(mapping, key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    static Object toPlainData(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                result.put(String.valueOf(entry.getKey()), toPlainData(entry.getValue()));
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            for (Object entry : collection) {
                result.add(toPlainData(entry));
            }
            return result;
        }
        return value;
    }

    static String normalizeId(Object value) {
        return toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    static String toStringSafe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static boolean isBlank(Object value) {
        return toStringSafe(value).trim().isEmpty();
    }

    static Integer tryParseInt(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(toStringSafe(value).trim());
        } catch (Exception _) {
            return defaultValue;
        }
    }

    static Long tryParseLong(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(toStringSafe(value).trim());
        } catch (Exception _) {
            return defaultValue;
        }
    }

    static Double tryParseDouble(Object value, Double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(toStringSafe(value).trim());
        } catch (Exception _) {
            return defaultValue;
        }
    }

    private static Object get(Object mapping, String key) {
        if (!(mapping instanceof Map<?, ?> map) || key == null) {
            return null;
        }
        return map.get(key);
    }
}
