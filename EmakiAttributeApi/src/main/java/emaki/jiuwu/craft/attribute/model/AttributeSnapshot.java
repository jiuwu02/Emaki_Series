package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;

/**
 * Immutable snapshot of an entity's resolved attribute values at a point in
 * time.
 *
 * <p>Used to capture an attacker's or target's combat-relevant attributes so a
 * damage calculation can be performed consistently (including asynchronously)
 * without re-reading live entity state. Supports serialization to and from a
 * plain map for persistence.
 *
 * @param schemaVersion   the snapshot schema version
 * @param sourceSignature a signature describing the source data set; never
 *                        {@code null}
 * @param values          attribute id to value mapping; never {@code null}
 * @param updatedAt       epoch millis when the snapshot was taken
 */
public record AttributeSnapshot(int schemaVersion,
        String sourceSignature,
        Map<String, Double> values,
        long updatedAt) {

    /** Current snapshot schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Canonical constructor; normalizes a {@code null} signature to an empty
     * string and defensively copies {@code values}.
     */
    public AttributeSnapshot    {
        sourceSignature = sourceSignature == null ? "" : sourceSignature;
        values = values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
    }

    /**
     * Creates an empty snapshot carrying only the given signature.
     *
     * @param sourceSignature the source signature to attach
     * @return a snapshot with no attribute values
     */
    public static AttributeSnapshot empty(String sourceSignature) {
        return new AttributeSnapshot(CURRENT_SCHEMA_VERSION, sourceSignature, Map.of(), System.currentTimeMillis());
    }

    /** {@return this snapshot serialized to a plain, persistable map} */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schema_version", schemaVersion);
        map.put("source_signature", sourceSignature);
        map.put("updated_at", updatedAt);
        map.put("values", new LinkedHashMap<>(values));
        return map;
    }

    /**
     * Reconstructs a snapshot from a map produced by {@link #toMap()}.
     *
     * @param map the serialized form; may be {@code null}
     * @return the reconstructed snapshot, or {@code null} if {@code map} is
     *         {@code null}
     */
    public static AttributeSnapshot fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Map<String, Double> values = new LinkedHashMap<>();
        Object valuesRaw = map.get("values");
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(valuesRaw).entrySet()) {
            values.put(entry.getKey().toLowerCase(Locale.ROOT), Numbers.tryParseDouble(entry.getValue(), 0D));
        }
        return new AttributeSnapshot(
                Numbers.tryParseInt(map.get("schema_version"), CURRENT_SCHEMA_VERSION),
                ConfigNodes.string(map, "source_signature", ""),
                values,
                Numbers.tryParseLong(map.get("updated_at"), System.currentTimeMillis())
        );
    }
}
