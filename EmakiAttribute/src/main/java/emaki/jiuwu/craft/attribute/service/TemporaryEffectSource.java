package emaki.jiuwu.craft.attribute.service;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public enum TemporaryEffectSource {

    CORE_ACTION,

    MYTHIC,

    INTERNAL;

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TemporaryEffectSource fromLabel(String raw, TemporaryEffectSource fallback) {
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
