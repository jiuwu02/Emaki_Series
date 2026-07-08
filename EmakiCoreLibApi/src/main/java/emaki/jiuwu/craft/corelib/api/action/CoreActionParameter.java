package emaki.jiuwu.craft.corelib.api.action;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes one string argument accepted by a CoreLib action.
 */
public record CoreActionParameter(
        @NotNull String name,
        @NotNull CoreActionParameterType type,
        boolean required,
        @NotNull String defaultValue,
        @NotNull String description) {

    public CoreActionParameter {
        name = name == null ? "" : name;
        type = type == null ? CoreActionParameterType.STRING : type;
        defaultValue = defaultValue == null ? "" : defaultValue;
        description = description == null ? "" : description;
    }

    public static @NotNull CoreActionParameter required(@NotNull String name,
            @Nullable CoreActionParameterType type,
            @Nullable String description) {
        return new CoreActionParameter(name, type == null ? CoreActionParameterType.STRING : type, true, "", description);
    }

    public static @NotNull CoreActionParameter optional(@NotNull String name,
            @Nullable CoreActionParameterType type,
            @Nullable String defaultValue,
            @Nullable String description) {
        return new CoreActionParameter(name, type == null ? CoreActionParameterType.STRING : type, false, defaultValue, description);
    }
}
