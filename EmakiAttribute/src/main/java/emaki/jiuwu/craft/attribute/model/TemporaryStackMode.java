package emaki.jiuwu.craft.attribute.model;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.text.Texts;

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

    public static boolean isDeclared(String raw) {
        if (Texts.isBlank(raw)) {
            return false;
        }
        try {
            valueOf(raw.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
