package emaki.jiuwu.craft.corelib.assembly;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record EmakiStatContribution(String statId, double amount, String sourceId, int sequence) {

    public EmakiStatContribution {
        statId = normalizeId(statId);
        sourceId = Texts.isBlank(sourceId) ? statId : Texts.toStringSafe(sourceId);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stat_id", statId);
        map.put("amount", amount);
        map.put("source_id", sourceId);
        map.put("sequence", sequence);
        return map;
    }

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
