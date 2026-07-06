package emaki.jiuwu.craft.attribute.script.js;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeTargetType;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.attribute.model.TemporaryStackMode;
import emaki.jiuwu.craft.corelib.text.Texts;

final class ScriptAttributeDefinitionParser {

    private ScriptAttributeDefinitionParser() {
    }

    static AttributeDefinition parse(Map<String, ?> definition) {
        if (definition == null) {
            return null;
        }
        return new AttributeDefinition(
                string(definition, "id", ""),
                string(definition, "displayName", string(definition, "display_name", "")),
                enumValue(AttributeValueKind.class, string(definition, "valueKind", string(definition, "value_kind", "FLAT")), AttributeValueKind.FLAT),
                enumValue(AttributeTargetType.class, string(definition, "targetType", string(definition, "target_type", "GENERIC")), AttributeTargetType.GENERIC),
                string(definition, "targetId", string(definition, "target_id", "")),
                string(definition, "mmoItemsStat", string(definition, "mmoitems_stat", "")),
                number(definition.get("defaultValue"), number(definition.get("default_value"), 0D)),
                nullableNumber(definition.containsKey("minValue") ? definition.get("minValue") : definition.get("min_value")),
                nullableNumber(definition.containsKey("maxValue") ? definition.get("maxValue") : definition.get("max_value")),
                bool(definition, "allowNegative", bool(definition, "allow_negative", true)),
                (int) Math.round(number(definition.get("priority"), 0D)),
                string(definition, "loreFormatId", string(definition, "lore_format_id", "")),
                stringList(definition.containsKey("lorePatterns") ? definition.get("lorePatterns") : definition.get("lore_patterns")),
                string(definition, "description", ""),
                number(definition.get("attributePower"), number(definition.get("attribute_power"), 1D)),
                stringList(definition.containsKey("tags") ? definition.get("tags") : definition.get("tag")),
                enumValue(TemporaryStackMode.class, string(definition, "temporaryStackMode", string(definition, "temporary_stack_mode", "REPLACE")), TemporaryStackMode.REPLACE)
        );
    }

    private static String string(Map<String, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : Texts.toStringSafe(value);
    }

    private static boolean bool(Map<String, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value == null ? fallback : Boolean.parseBoolean(Texts.toStringSafe(value));
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.toStringSafe(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static Double nullableNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.toStringSafe(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (Object entry : iterable) {
            if (entry != null) {
                result.add(Texts.toStringSafe(entry));
            }
        }
        return List.copyOf(result);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String raw, T fallback) {
        try {
            return Enum.valueOf(type, Texts.toStringSafe(raw).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
