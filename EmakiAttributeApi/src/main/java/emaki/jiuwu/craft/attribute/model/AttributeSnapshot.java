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
     * Suffix marking a companion entry that stores the random "spread" (upper
     * bound minus lower bound) of a ranged attribute value parsed from a source
     * such as {@code 物理伤害: 1-5}.
     *
     * <p>The base attribute id keeps the lower bound (so every existing reader
     * sees a safe, deterministic value) while {@code <id>$range_spread} carries
     * {@code max - min}. Both entries live in the same {@link #values} map, so
     * additive merges across equipment combine ranges correctly. A spread of
     * {@code 0} (or a missing companion) means the attribute is a plain scalar.
     */
    public static final String RANGE_SPREAD_SUFFIX = "$range_spread";

    /**
     * {@return the companion spread key for an attribute id}
     *
     * @param attributeId the base attribute id
     */
    public static String rangeSpreadKey(String attributeId) {
        return attributeId == null ? RANGE_SPREAD_SUFFIX : attributeId + RANGE_SPREAD_SUFFIX;
    }

    /**
     * {@return whether a values-map key is a range-spread companion key}
     *
     * @param key the key to test
     */
    public static boolean isRangeSpreadKey(String key) {
        return key != null && key.endsWith(RANGE_SPREAD_SUFFIX);
    }

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
