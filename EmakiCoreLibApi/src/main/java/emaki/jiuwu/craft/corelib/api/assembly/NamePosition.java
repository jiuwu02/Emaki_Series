package emaki.jiuwu.craft.corelib.api.assembly;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public enum NamePosition {
    PREFIX,
    POSTFIX;

    public static NamePosition fromValue(Object value) {
        String normalized = Texts.toStringSafe(value).trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return POSTFIX;
        }
        try {
            return NamePosition.valueOf(normalized);
        } catch (IllegalArgumentException _) {
            return POSTFIX;
        }
    }
}
