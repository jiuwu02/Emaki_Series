package emaki.jiuwu.craft.strengthen.enhancement.cost;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public enum TargetCompareEnum {

    NONE(""),
    SAME_AFFIX("same_affix"),
    SAME_AFFIX_SET("same_affix_set"),
    SAME_ITEM_TYPE("same_item_type"),
    SAME_LEVEL("same_level"),
    LEVEL_AT_LEAST("level_at_least");

    private final String token;

    TargetCompareEnum(String token) {
        this.token = token;
    }

    public @NotNull String token() {
        return token;
    }

    public static @NotNull TargetCompareEnum fromStringOrDefault(@Nullable String raw,
            @NotNull TargetCompareEnum fallback) {
        if (Texts.isBlank(raw)) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (TargetCompareEnum candidate : values()) {
            if (candidate.token.equals(normalized) || candidate.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return candidate;
            }
        }
        return fallback;
    }

    public static @NotNull String legalTokens() {
        StringBuilder builder = new StringBuilder();
        for (TargetCompareEnum candidate : values()) {
            if (candidate == NONE) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(candidate.token);
        }
        return builder.toString();
    }
}
