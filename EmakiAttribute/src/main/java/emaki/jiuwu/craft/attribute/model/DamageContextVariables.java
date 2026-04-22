package emaki.jiuwu.craft.attribute.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record DamageContextVariables(Map<String, Object> values) {

    public DamageContextVariables {
        values = normalize(values);
    }

    public static DamageContextVariables empty() {
        return new DamageContextVariables(Map.of());
    }

    public static DamageContextVariables from(Map<String, ?> values) {
        return new DamageContextVariables(values == null ? Map.of() : copy(values));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Builder toBuilder() {
        Builder builder = builder();
        builder.putAll(values);
        return builder;
    }

    public DamageContextVariables with(String key, Object value) {
        return toBuilder().put(key, value).build();
    }

    public DamageContextVariables merge(DamageContextVariables other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        return toBuilder().putAll(other).build();
    }

    public Object get(String key) {
        if (Texts.isBlank(key)) {
            return null;
        }
        return values.get(Texts.normalizeId(key));
    }

    public String string(String key, String fallback) {
        Object value = get(key);
        String result = Texts.toStringSafe(value).trim();
        return Texts.isBlank(result) ? fallback : result;
    }

    public double doubleValue(String key, double fallback) {
        Double value = Numbers.tryParseDouble(get(key), null);
        return value == null ? fallback : value;
    }

    public double getDouble(String key, double fallback) {
        return doubleValue(key, fallback);
    }

    public boolean getBoolean(boolean fallback, String... keys) {
        if (keys == null || keys.length == 0) {
            return fallback;
        }
        for (String key : keys) {
            if (Texts.isBlank(key) || !contains(key)) {
                continue;
            }
            Object raw = get(key);
            if (raw instanceof Boolean boolValue) {
                return boolValue;
            }
            String normalized = Texts.toStringSafe(raw).trim().toLowerCase(Locale.ROOT);
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
        String normalized = Texts.normalizeId(String.valueOf(raw));
        if (Texts.isBlank(normalized)) {
            return null;
        }
        try {
            return EntityDamageEvent.DamageCause.valueOf(normalized.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean contains(String key) {
        return !Texts.isBlank(key) && values.containsKey(Texts.normalizeId(key));
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
            normalized.put(Texts.normalizeId(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
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

    public static final class Builder {

        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder put(String key, Object value) {
            if (Texts.isBlank(key) || value == null) {
                return this;
            }
            values.put(Texts.normalizeId(key), ConfigNodes.toPlainData(value));
            return this;
        }

        public Builder putAll(Map<String, ?> entries) {
            if (entries == null || entries.isEmpty()) {
                return this;
            }
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public Builder putAll(DamageContextVariables variables) {
            if (variables == null || variables.isEmpty()) {
                return this;
            }
            return putAll(variables.asMap());
        }

        public DamageContextVariables build() {
            return new DamageContextVariables(values);
        }
    }
}
