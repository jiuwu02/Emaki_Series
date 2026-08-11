package emaki.jiuwu.craft.corelib.api.action;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One {@code key=value} argument accepted by a pipeline stage.
 *
 * @param name argument name as written in the pipeline
 * @param type declared value shape
 * @param required whether omitting it is a compile-time error
 * @param defaultValue value used when the argument is absent and not required
 * @param positional whether the value may be written bare, without {@code name=}
 * @param description short human-readable purpose
 */
public record CoreStageParameter(@NotNull String name,
        @NotNull CoreStageParameterType type,
        boolean required,
        @NotNull String defaultValue,
        boolean positional,
        @NotNull String description) {

    public CoreStageParameter {
        name = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        type = type == null ? CoreStageParameterType.STRING : type;
        defaultValue = defaultValue == null ? "" : defaultValue;
        description = description == null ? "" : description;
    }

    /** {@return a required named parameter} */
    public static @NotNull CoreStageParameter required(@NotNull String name,
            @Nullable CoreStageParameterType type,
            @Nullable String description) {
        return new CoreStageParameter(name, type, true, "", false, description);
    }

    /** {@return an optional named parameter with a default} */
    public static @NotNull CoreStageParameter optional(@NotNull String name,
            @Nullable CoreStageParameterType type,
            @Nullable String defaultValue,
            @Nullable String description) {
        return new CoreStageParameter(name, type, false, defaultValue, false, description);
    }

    /**
     * Creates a required parameter whose value may be written without {@code name=}.
     *
     * <p>Used by stages like {@code run <sequence>} and {@code where <condition>} where a bare value
     * reads better than a named argument.</p>
     *
     * @param name argument name
     * @param type declared value shape
     * @param description short human-readable purpose
     * @return the parameter
     */
    public static @NotNull CoreStageParameter positional(@NotNull String name,
            @Nullable CoreStageParameterType type,
            @Nullable String description) {
        return new CoreStageParameter(name, type, true, "", true, description);
    }
}
