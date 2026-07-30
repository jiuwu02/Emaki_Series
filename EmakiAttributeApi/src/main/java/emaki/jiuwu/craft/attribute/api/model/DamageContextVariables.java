package emaki.jiuwu.craft.attribute.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.event.entity.EntityDamageEvent;


/**
 * Immutable, normalized bag of context variables attached to a
 * {@link DamageContext}.
 *
 * <p>Keys are normalized (lower-cased identifier form) and values are reduced to
 * plain data so they can safely back damage-stage expressions, debug output and
 * serialization. Provides typed accessors with fallbacks and a {@link Builder}
 * for incremental construction.
 *
 * @param values the underlying immutable, normalized key/value map
 */
public record DamageContextVariables(Map<String, Object> values) {

    /** Canonical constructor; normalizes keys and values to plain data. */
    public DamageContextVariables {
        values = normalize(values);
    }

    /** {@return a shared empty instance} */
    public static DamageContextVariables empty() {
        return new DamageContextVariables(Map.of());
    }

    /**
     * Creates an instance from an arbitrary map, normalizing its contents.
     *
     * @param values the source map; may be {@code null}
     * @return the new instance
     */
    public static DamageContextVariables from(Map<String, ?> values) {
        return new DamageContextVariables(values == null ? Map.of() : copy(values));
    }

    /** {@return a new empty {@link Builder}} */
    public static Builder builder() {
        return new Builder();
    }

    /** {@return the underlying immutable variable map} */
    public Map<String, Object> asMap() {
        return values;
    }

    /** {@return whether there are no variables} */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** {@return a {@link Builder} pre-populated with this instance's entries} */
    public Builder toBuilder() {
        Builder builder = builder();
        builder.putAll(values);
        return builder;
    }

    /**
     * {@return a copy with one key set to the given value}
     *
     * @param key   the variable key
     * @param value the value to associate
     */
    public DamageContextVariables with(String key, Object value) {
        return toBuilder().put(key, value).build();
    }

    /**
     * Merges another variable set on top of this one.
     *
     * @param other the variables to overlay; {@code null}/empty returns
     *              {@code this}
     * @return the merged instance
     */
    public DamageContextVariables merge(DamageContextVariables other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        return toBuilder().putAll(other).build();
    }

    /**
     * {@return the raw value for a key, or {@code null} if absent/blank key}
     *
     * @param key the variable key (normalized before lookup)
     */
    public Object get(String key) {
        if (AttributeApiValues.isBlank(key)) {
            return null;
        }
        return values.get(AttributeApiValues.normalizeId(key));
    }

    /**
     * {@return the value as a trimmed string, or {@code fallback} when missing}
     *
     * @param key      the variable key
     * @param fallback value returned when the key is missing or blank
     */
    public String string(String key, String fallback) {
        Object value = get(key);
        String result = AttributeApiValues.toStringSafe(value).trim();
        return AttributeApiValues.isBlank(result) ? fallback : result;
    }

    /**
     * {@return the value parsed as a double, or {@code fallback} when missing}
     *
     * @param key      the variable key
     * @param fallback value returned when the key is missing or unparsable
     */
    public double doubleValue(String key, double fallback) {
        Double value = AttributeApiValues.tryParseDouble(get(key), null);
        return value == null ? fallback : value;
    }

    /**
     * Alias for {@link #doubleValue(String, double)}.
     *
     * @param key      the variable key
     * @param fallback value returned when the key is missing or unparsable
     * @return the parsed double or the fallback
     */
    public double getDouble(String key, double fallback) {
        return doubleValue(key, fallback);
    }

    /**
     * Resolves the first boolean-like value among the given keys.
     *
     * <p>Recognizes {@link Boolean} values as well as the strings
     * {@code true/false}, {@code yes/no} and {@code 1/0} (case-insensitive).
     *
     * @param fallback value returned when no key yields a recognizable boolean
     * @param keys     the keys to probe, in priority order
     * @return the resolved boolean or {@code fallback}
     */
    public boolean getBoolean(boolean fallback, String... keys) {
        if (keys == null || keys.length == 0) {
            return fallback;
        }
        for (String key : keys) {
            if (AttributeApiValues.isBlank(key) || !contains(key)) {
                continue;
            }
            Object raw = get(key);
            if (raw instanceof Boolean boolValue) {
                return boolValue;
            }
            String normalized = AttributeApiValues.toStringSafe(raw).trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) {
                continue;
            }
            if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
                return false;
            }
        }
        return fallback;
    }

    /**
     * Extracts a Bukkit damage cause from the {@code cause},
     * {@code damage_cause} or {@code damage_cause_id} variables.
     *
     * @return the resolved cause, or {@code null} if none is present or parsable
     */
    public EntityDamageEvent.DamageCause extractDamageCause() {
        if (isEmpty()) {
            return null;
        }
        Object raw = get("cause");
        if (raw == null) {
            raw = get("damage_cause");
        }
        if (raw == null) {
            raw = get("damage_cause_id");
        }
        if (raw == null) {
            return null;
        }
        if (raw instanceof EntityDamageEvent.DamageCause cause) {
            return cause;
        }
        String normalized = AttributeApiValues.normalizeId(String.valueOf(raw));
        if (AttributeApiValues.isBlank(normalized)) {
            return null;
        }
        try {
            return EntityDamageEvent.DamageCause.valueOf(normalized.trim().toUpperCase(Locale.ROOT));
        } catch (Exception _) {
            return null;
        }
    }

    /**
     * {@return whether a (normalized) key is present}
     *
     * @param key the variable key
     */
    public boolean contains(String key) {
        return !AttributeApiValues.isBlank(key) && values.containsKey(AttributeApiValues.normalizeId(key));
    }

    private static Map<String, Object> normalize(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(AttributeApiValues.normalizeId(entry.getKey()), AttributeApiValues.toPlainData(entry.getValue()));
        }
        if (normalized.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(normalized);
    }

    private static Map<String, Object> copy(Map<String, ?> values) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    /**
     * Mutable builder for {@link DamageContextVariables}. Keys are normalized
     * and {@code null} keys/values are ignored.
     */
    public static final class Builder {

        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Adds or replaces a single entry.
         *
         * @param key   the variable key; ignored when blank
         * @param value the value; ignored when {@code null}
         * @return this builder
         */
        public Builder put(String key, Object value) {
            if (AttributeApiValues.isBlank(key) || value == null) {
                return this;
            }
            values.put(AttributeApiValues.normalizeId(key), AttributeApiValues.toPlainData(value));
            return this;
        }

        /**
         * Adds all entries from a plain map.
         *
         * @param entries the entries to add; {@code null}/empty is a no-op
         * @return this builder
         */
        public Builder putAll(Map<String, ?> entries) {
            if (entries == null || entries.isEmpty()) {
                return this;
            }
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * Adds all entries from another variable set.
         *
         * @param variables the variables to add; {@code null}/empty is a no-op
         * @return this builder
         */
        public Builder putAll(DamageContextVariables variables) {
            if (variables == null || variables.isEmpty()) {
                return this;
            }
            return putAll(variables.asMap());
        }

        /** {@return an immutable {@link DamageContextVariables} of the entries} */
        public DamageContextVariables build() {
            return new DamageContextVariables(values);
        }
    }
}
