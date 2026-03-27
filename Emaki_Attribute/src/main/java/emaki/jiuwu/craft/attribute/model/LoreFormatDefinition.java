package emaki.jiuwu.craft.attribute.model;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.Locale;

public record LoreFormatDefinition(String id,
                                   String format,
                                   int precision) {

    public LoreFormatDefinition {
        id = normalizeId(id);
        format = Texts.isBlank(format) ? "{sign}{value} {name}" : Texts.toStringSafe(format).trim();
    }

    public static LoreFormatDefinition fromMap(Object raw) {
        if (raw == null) {
            return null;
        }
        return new LoreFormatDefinition(
            ConfigNodes.string(raw, "id", null),
            ConfigNodes.string(raw, "format", null),
            Numbers.tryParseInt(ConfigNodes.get(raw, "precision"), 2)
        );
    }

    private static String normalizeId(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
