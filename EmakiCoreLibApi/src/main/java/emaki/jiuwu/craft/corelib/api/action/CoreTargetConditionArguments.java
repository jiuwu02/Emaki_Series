package emaki.jiuwu.craft.corelib.api.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Read-only view over the fields of one condition node.
 *
 * <p>Field names are matched case-insensitively. Accessors that return an {@link Optional} stay empty
 * both when a field is absent and when it cannot be read as the asked shape, so an implementation can
 * answer {@link CoreTargetOutcome#UNKNOWN} instead of substituting a permissive default.</p>
 */
public final class CoreTargetConditionArguments {

    private static final CoreTargetConditionArguments EMPTY = new CoreTargetConditionArguments(Map.of());

    private final Map<String, Object> fields;

    private CoreTargetConditionArguments(Map<String, Object> fields) {
        this.fields = fields;
    }

    /**
     * Creates a view over the fields of one condition node.
     *
     * @param fields the node fields, possibly {@code null}
     * @return the arguments view, empty when no field is readable
     */
    public static @NotNull CoreTargetConditionArguments of(@Nullable Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return EMPTY;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey().trim().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return normalized.isEmpty() ? EMPTY : new CoreTargetConditionArguments(Map.copyOf(normalized));
    }

    /** {@return a view over one YAML node's entries} */
    public static @NotNull CoreTargetConditionArguments fromNode(@Nullable Object node) {
        return of(ConfigNodes.entries(node));
    }

    /** {@return an empty arguments view} */
    public static @NotNull CoreTargetConditionArguments empty() {
        return EMPTY;
    }

    /** {@return every field, keyed by lowercase name} */
    public @NotNull Map<String, Object> raw() {
        return fields;
    }

    /** {@return whether {@code key} is present with a non-blank value} */
    public boolean has(@Nullable String key) {
        Object value = value(key);
        return value != null && Texts.isNotBlank(String.valueOf(value));
    }

    /** {@return the raw value of {@code key}, or {@code null} when absent} */
    public @Nullable Object value(@Nullable String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return fields.get(key.trim().toLowerCase(Locale.ROOT));
    }

    /** {@return {@code key} as text, or {@code fallback} when absent} */
    public @NotNull String string(@Nullable String key, @NotNull String fallback) {
        Object value = value(key);
        return value == null ? fallback : String.valueOf(value);
    }

    /**
     * Reads {@code key} as a list of strings.
     *
     * <p>A single scalar is returned as a one-element list, so {@code value: ZOMBIE} and
     * {@code value: [ZOMBIE, SKELETON]} read the same way.</p>
     *
     * @param key field name
     * @return the values, never {@code null}
     */
    public @NotNull List<String> strings(@Nullable String key) {
        Object value = value(key);
        if (value == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(value)) {
            if (entry == null) {
                continue;
            }
            String text = Texts.trim(String.valueOf(entry));
            if (Texts.isNotBlank(text)) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    /** {@return {@code key} as a whole number, empty when absent or unparseable} */
    public @NotNull OptionalInt intValue(@Nullable String key) {
        Integer parsed = Numbers.tryParseInt(value(key), null);
        return parsed == null ? OptionalInt.empty() : OptionalInt.of(parsed);
    }

    /** {@return {@code key} as a decimal number, empty when absent or unparseable} */
    public @NotNull OptionalDouble doubleValue(@Nullable String key) {
        Double parsed = Numbers.tryParseDouble(value(key), null);
        return parsed == null ? OptionalDouble.empty() : OptionalDouble.of(parsed);
    }

    /** {@return {@code key} as a boolean, empty when absent or unparseable} */
    public @NotNull Optional<Boolean> booleanValue(@Nullable String key) {
        Object value = value(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Boolean flag) {
            return Optional.of(flag);
        }
        String text = Texts.lower(String.valueOf(value));
        if ("true".equals(text) || "yes".equals(text) || "on".equals(text)) {
            return Optional.of(true);
        }
        if ("false".equals(text) || "no".equals(text) || "off".equals(text)) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    /** {@return {@code key} as a Bukkit entity type, empty when absent or unknown} */
    public @NotNull Optional<EntityType> entityType(@Nullable String key) {
        Object value = value(key);
        if (value == null) {
            return Optional.empty();
        }
        String normalized = Texts.trim(String.valueOf(value))
                .replace("minecraft:", "")
                .replace('.', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return Optional.of(EntityType.valueOf(normalized));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}