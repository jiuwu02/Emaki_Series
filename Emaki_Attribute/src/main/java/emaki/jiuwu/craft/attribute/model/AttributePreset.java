package emaki.jiuwu.craft.attribute.model;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record AttributePreset(String id,
                              String displayName,
                              int priority,
                              Map<String, Double> values,
                              String description) {

    public AttributePreset {
        id = normalizeId(id);
        displayName = Texts.isBlank(displayName) ? id : Texts.toStringSafe(displayName).trim();
        values = values == null ? Map.of() : Map.copyOf(values);
        description = Texts.toStringSafe(description).trim();
    }

    public static AttributePreset fromMap(Object raw) {
        if (raw == null) {
            return null;
        }
        Map<String, Double> values = new LinkedHashMap<>();
        Object valuesRaw = ConfigNodes.get(raw, "values");
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(valuesRaw).entrySet()) {
            values.put(entry.getKey().toLowerCase(Locale.ROOT), Numbers.tryParseDouble(entry.getValue(), 0D));
        }
        return new AttributePreset(
            ConfigNodes.string(raw, "id", null),
            ConfigNodes.string(raw, "display_name", null),
            Numbers.tryParseInt(ConfigNodes.get(raw, "priority"), 0),
            values,
            ConfigNodes.string(raw, "description", null)
        );
    }

    private static String normalizeId(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
