package emaki.jiuwu.craft.strengthen.enhancement.cost;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public enum ConsumeTimingEnum {
    ALWAYS,
    SUCCESS,
    FAILURE,
    NEVER;

    public static @Nullable ConsumeTimingEnum fromString(@Nullable String text) {
        if (Texts.isBlank(text)) {
            return null;
        }
        try {
            return ConsumeTimingEnum.valueOf(Texts.trim(text).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    public static @NotNull ConsumeTimingEnum fromStringOrDefault(@Nullable String text, @NotNull ConsumeTimingEnum defaultValue) {
        ConsumeTimingEnum result = fromString(text);
        return result != null ? result : defaultValue;
    }
}
