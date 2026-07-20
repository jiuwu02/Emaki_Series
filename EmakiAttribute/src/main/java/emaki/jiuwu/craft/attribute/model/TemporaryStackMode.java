package emaki.jiuwu.craft.attribute.model;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.text.Texts;






public enum TemporaryStackMode {




    REPLACE,





    STACK;

    public static TemporaryStackMode fromString(String raw, TemporaryStackMode fallback) {
        if (Texts.isBlank(raw)) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
