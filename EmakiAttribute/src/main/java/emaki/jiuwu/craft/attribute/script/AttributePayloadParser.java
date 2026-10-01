package emaki.jiuwu.craft.attribute.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.graalvm.polyglot.Value;

import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.AttributeTargetType;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.attribute.model.TemporaryStackMode;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class AttributePayloadParser {

    private static final String UNKNOWN_FILE = "unknown";

    private AttributePayloadParser() {
    }

    public enum ErrorKind {
        MISSING_ID,
        INVALID_ENUM,
        INVALID_NUMBER,
        INVALID_LIST
    }

    public record PayloadError(ErrorKind kind, String field, String value, String file) {
    }

    public record ParseResult(AttributeDefinition definition, List<PayloadError> errors) {

        public ParseResult {
            errors = List.copyOf(errors);
        }

        public boolean valid() {
            return definition != null && errors.isEmpty();
        }
    }

    public static ParseResult parse(Value payload) {
        String file = UNKNOWN_FILE;
        List<PayloadError> errors = new ArrayList<>();
        if (payload == null || !payload.hasMembers()) {
            errors.add(new PayloadError(ErrorKind.MISSING_ID, "id", textOf(payload), file));
            return new ParseResult(null, errors);
        }
        Value rawId = member(payload, "id");
        String id = textOf(rawId);
        if (Texts.isBlank(id)) {
            errors.add(new PayloadError(ErrorKind.MISSING_ID, "id", textOf(rawId), file));
        }
        AttributeValueKind valueKind = enumField(payload, "value_kind", AttributeValueKind.FLAT, AttributeValueKind.class, errors, file);
        AttributeTargetType targetType = enumField(payload, "target_type", AttributeTargetType.GENERIC, AttributeTargetType.class, errors, file);
        TemporaryStackMode temporaryStackMode = enumField(payload, "temporary_stack_mode", TemporaryStackMode.REPLACE, TemporaryStackMode.class, errors, file);
        Double defaultValue = numericField(payload, "default_value", errors, file);
        Double minValue = numericField(payload, "min_value", errors, file);
        Double maxValue = numericField(payload, "max_value", errors, file);
        Double attributePower = numericField(payload, "attribute_power", errors, file);
        Double priority = numericField(payload, "priority", errors, file);
        List<String> lorePatterns = listField(payload, "lore_patterns", errors, file);
        List<String> tags = listField(payload, "tags", errors, file);
        if (!errors.isEmpty()) {
            return new ParseResult(null, errors);
        }
        AttributeDefinition definition = new AttributeDefinition(
                id,
                textOf(member(payload, "display_name")),
                valueKind,
                targetType,
                textOf(member(payload, "target_id")),
                textOf(member(payload, "mmoitems_stat")),
                defaultValue == null ? 0D : defaultValue,
                minValue,
                maxValue,
                booleanField(payload, "allow_negative", true),
                (int) (priority == null ? 0D : priority),
                textOf(member(payload, "lore_format_id")),
                lorePatterns,
                textOf(member(payload, "description")),
                attributePower == null ? 1D : attributePower,
                tags,
                temporaryStackMode,
                false,
                null
        );
        return new ParseResult(definition, errors);
    }

    private static Value member(Value payload, String name) {
        if (payload == null || !payload.hasMember(name)) {
            return null;
        }
        Value value = payload.getMember(name);
        return value == null || value.isNull() ? null : value;
    }

    private static String textOf(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isString()) {
            return value.asString();
        }
        return Texts.toStringSafe(value.toString());
    }

    private static Double numericField(Value payload, String name, List<PayloadError> errors, String file) {
        Value value = member(payload, name);
        if (value == null) {
            return null;
        }
        Double parsed = numberOf(value);
        if (parsed == null) {
            errors.add(new PayloadError(ErrorKind.INVALID_NUMBER, name, textOf(value), file));
            return null;
        }
        return parsed;
    }

    private static Double numberOf(Value value) {
        Double parsed = null;
        if (value.isNumber()) {
            parsed = value.asDouble();
        } else if (value.isString()) {
            String text = value.asString();
            if (Texts.isNotBlank(text)) {
                parsed = Numbers.tryParseDouble(text, null);
            }
        }
        return parsed == null || !Double.isFinite(parsed) ? null : parsed;
    }

    private static boolean booleanField(Value payload, String name, boolean fallback) {
        Value value = member(payload, name);
        return value != null && value.isBoolean() ? value.asBoolean() : fallback;
    }

    private static <E extends Enum<E>> E enumField(Value payload,
            String name,
            E fallback,
            Class<E> type,
            List<PayloadError> errors,
            String file) {
        Value value = member(payload, name);
        if (value == null) {
            return fallback;
        }
        String text = textOf(value);
        E parsed = parseEnum(text, type);
        if (parsed == null) {
            errors.add(new PayloadError(ErrorKind.INVALID_ENUM, name, text, file));
            return fallback;
        }
        return parsed;
    }

    private static <E extends Enum<E>> E parseEnum(String text, Class<E> type) {
        if (Texts.isBlank(text)) {
            return null;
        }
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static List<String> listField(Value payload, String name, List<PayloadError> errors, String file) {
        Value value = member(payload, name);
        if (value == null) {
            return List.of();
        }
        if (!value.hasArrayElements()) {
            errors.add(new PayloadError(ErrorKind.INVALID_LIST, name, textOf(value), file));
            return List.of();
        }
        List<String> items = new ArrayList<>();
        long size = value.getArraySize();
        for (long index = 0; index < size; index++) {
            String item = textOf(value.getArrayElement(index));
            if (Texts.isNotBlank(item)) {
                items.add(item);
            }
        }
        return items;
    }
}
