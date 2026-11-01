package emaki.jiuwu.craft.corelib.api.assembly;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * One numeric stat value contributed by a namespace layer to an assembled item, referenced by
 * name and lore templates through its {@code statId} while rendering.
 *
 * <p>The compact constructor normalizes {@code statId}: it is trimmed, lowercased with
 * {@link Locale#ROOT} and has spaces replaced by {@code _}, falling back to {@code unknown} when
 * blank. A blank {@code sourceId} falls back to the normalized {@code statId}. Rendering sorts
 * contributions ascending by {@code sequence} and breaks ties by {@code statId}.
 *
 * @param statId identifier templates use to look the value up
 * @param amount the contributed value
 * @param sourceId identifies the layer or operation that produced the value
 * @param sequence ascending ordering key controlling render order
 */
public record EmakiStatContribution(String statId, double amount, String sourceId, int sequence) {

    /** Normalizes the raw components as described in the type documentation. */
    public EmakiStatContribution    {
        statId = normalizeId(statId);
        sourceId = Texts.isBlank(sourceId) ? statId : Texts.toStringSafe(sourceId);
    }

    /**
     * {@return the contribution as a plain map with the {@code stat_id}, {@code amount},
     * {@code source_id} and {@code sequence} keys}
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stat_id", statId);
        map.put("amount", amount);
        map.put("source_id", sourceId);
        map.put("sequence", sequence);
        return map;
    }

    /**
     * Reads a contribution back from its {@link #toMap()} form.
     *
     * @param map plain map; {@code null} and empty are accepted
     * @return the parsed contribution, or {@code null} when the map is {@code null}, empty, or
     *         carries a blank {@code stat_id}; unparsable {@code amount} and {@code sequence}
     *         values fall back to {@code 0}
     */
    public static EmakiStatContribution fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String statId = Texts.toStringSafe(map.get("stat_id"));
        if (Texts.isBlank(statId)) {
            return null;
        }
        return new EmakiStatContribution(
                statId,
                Numbers.tryParseDouble(map.get("amount"), 0D),
                Texts.toStringSafe(map.get("source_id")),
                Numbers.tryParseInt(map.get("sequence"), 0)
        );
    }

    private static String normalizeId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return normalized.isBlank() ? "unknown" : normalized;
    }
}
