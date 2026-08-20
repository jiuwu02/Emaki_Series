package emaki.jiuwu.craft.strengthen.enhancement.pity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public enum PityIsolationEnum {

    RECIPE("recipe"),
    MODE("mode"),
    TARGET("target"),
    AFFIX("affix"),
    LEVEL("level");

    private final String token;

    PityIsolationEnum(String token) {
        this.token = token;
    }

    public @NotNull String token() {
        return token;
    }

    public static @Nullable PityIsolationEnum fromToken(@Nullable String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (PityIsolationEnum candidate : values()) {
            if (candidate.token.equals(normalized)) {
                return candidate;
            }
        }
        return null;
    }

    public static @NotNull List<PityIsolationEnum> parseAll(@Nullable List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<PityIsolationEnum> resolved = new LinkedHashSet<>();
        for (String token : raw) {
            PityIsolationEnum dimension = fromToken(token);
            if (dimension != null) {
                resolved.add(dimension);
            }
        }
        return List.copyOf(new ArrayList<>(resolved));
    }

    public static @NotNull String legalTokens() {
        StringBuilder builder = new StringBuilder();
        for (PityIsolationEnum candidate : values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(candidate.token);
        }
        return builder.toString();
    }
}
