package emaki.jiuwu.craft.corelib.assembly;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.text.Texts;

public enum BaseNamePolicy {
    SOURCE_EFFECTIVE_NAME,
    SOURCE_TRANSLATABLE,
    EXPLICIT_TEMPLATE;

    public static BaseNamePolicy fromValue(Object value) {
        String normalized = Texts.toStringSafe(value).trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return SOURCE_EFFECTIVE_NAME;
        }
        try {
            return BaseNamePolicy.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return SOURCE_EFFECTIVE_NAME;
        }
    }
}
