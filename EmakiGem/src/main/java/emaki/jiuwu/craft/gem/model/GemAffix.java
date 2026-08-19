package emaki.jiuwu.craft.gem.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/** A rolled affix stored on a loose gem instance. */
public record GemAffix(String id, int stage, double value) {

    public GemAffix {
        id = Texts.lower(id);
        stage = Math.max(1, stage);
        value = Double.isFinite(value) ? value : 0D;
    }

    public String encode() {
        return id + "|" + stage + "|" + Double.toString(value);
    }

    public Map<String, Object> toMap() {
        return Map.of("id", id, "stage", stage, "value", value);
    }

    public static GemAffix fromMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        String id = Texts.toStringSafe(map.get("id"));
        if (Texts.isBlank(id)) {
            return null;
        }
        return new GemAffix(id,
                Numbers.tryParseInt(map.get("stage"), 1),
                Numbers.tryParseDouble(map.get("value"), 0D));
    }

    public static GemAffix decode(String encoded) {
        if (Texts.isBlank(encoded)) {
            return null;
        }
        String[] parts = encoded.split("\\|", -1);
        if (parts.length < 3 || Texts.isBlank(parts[0])) {
            return new GemAffix(encoded.trim(), 1, 0D);
        }
        return new GemAffix(parts[0], Numbers.tryParseInt(parts[1], 1),
                Numbers.tryParseDouble(parts[2], 0D));
    }

    public static List<GemAffix> decodeAll(List<String> encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return List.of();
        }
        List<GemAffix> result = new ArrayList<>();
        for (String value : encoded) {
            GemAffix affix = decode(value);
            if (affix != null && Texts.isNotBlank(affix.id())) {
                result.add(affix);
            }
        }
        return List.copyOf(result);
    }
}
