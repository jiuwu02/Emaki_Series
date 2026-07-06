package emaki.jiuwu.craft.attribute.model;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Determines how a temporary attribute behaves when the same temporary
 * attribute (identified by its effect id) is applied again while a previous
 * one is still active.
 */
public enum TemporaryStackMode {

    /**
     * The new value and duration replace the previous ones entirely.
     */
    REPLACE,

    /**
     * The new value is added to the previous value and the new duration is
     * added on top of the previous remaining duration.
     */
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
