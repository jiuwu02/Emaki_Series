package emaki.jiuwu.craft.strengthen.api.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class StrengthenApiValues {

    private static final Pattern MINI_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private StrengthenApiValues() {
    }

    static String toStringSafe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static boolean isBlank(Object value) {
        return toStringSafe(value).trim().isEmpty();
    }

    static boolean isNotBlank(Object value) {
        return !isBlank(value);
    }

    static String trim(Object value) {
        return toStringSafe(value).trim();
    }

    static String lower(Object value) {
        return toStringSafe(value).toLowerCase(Locale.ROOT);
    }

    static String normalizeId(String value) {
        return toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    static String stripMiniTags(Object value) {
        String text = toStringSafe(value);
        if (text.indexOf('<') < 0 || text.indexOf('>') < 0) {
            return text;
        }
        return MINI_TAG_PATTERN.matcher(text).replaceAll("");
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static Double tryParseDouble(Object value, Double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            String text = toStringSafe(value).trim();
            return text.isEmpty() ? fallback : Double.parseDouble(text);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static Object toPlainData(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), toPlainData(entry.getValue()));
                }
            }
            return Map.copyOf(result);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            for (Object entry : collection) {
                result.add(toPlainData(entry));
            }
            return List.copyOf(result);
        }
        return toStringSafe(value);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> plainMap(Object value) {
        Object plain = toPlainData(value);
        if (!(plain instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return Map.copyOf(result);
    }

    static double evaluateNumber(Object raw, Map<String, ?> variables) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        String expression = toStringSafe(raw).trim();
        if (expression.isEmpty()) {
            return 0D;
        }
        Double direct = tryParseDouble(expression, null);
        if (direct != null) {
            return direct;
        }
        if (variables != null && expression.startsWith("%") && expression.endsWith("%") && expression.length() > 2) {
            Object value = variables.get(expression.substring(1, expression.length() - 1).toLowerCase(Locale.ROOT));
            Double resolved = tryParseDouble(value, null);
            if (resolved != null) {
                return resolved;
            }
        }
        return 0D;
    }

    static Object evaluateMixed(Object raw, Map<String, ?> variables) {
        if (raw instanceof Number) {
            return raw;
        }
        String text = toStringSafe(raw).trim();
        if (text.startsWith("%") && text.endsWith("%") && text.length() > 2 && variables != null) {
            Object value = variables.get(text.substring(1, text.length() - 1).toLowerCase(Locale.ROOT));
            if (value != null) {
                return value;
            }
        }
        Double number = tryParseDouble(text, null);
        return number == null ? text : number;
    }
}
