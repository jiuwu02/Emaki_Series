package emaki.jiuwu.craft.corelib.api.action;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Public SPI for actions registered into EmakiCoreLib by other plugins.
 */
public interface CoreAction {

    long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    /** {@return the unique action id used in config action lines} */
    @NotNull
    String id();

    /** {@return a short human-readable description} */
    @NotNull
    String description();

    /** {@return a category used for listing and documentation} */
    @NotNull
    String category();

    /** {@return the action implementation version} */
    @NotNull
    default String version() {
        return "1.0.0";
    }

    /** {@return declared arguments accepted by this action} */
    @NotNull
    default List<CoreActionParameter> parameters() {
        return List.of();
    }

    /**
     * Allows dynamic arguments that are not declared in {@link #parameters()}.
     *
     * @param name argument name
     * @return true when the argument should be accepted
     */
    default boolean acceptsDynamicParameter(@Nullable String name) {
        return false;
    }

    /** {@return where this action may execute} */
    @NotNull
    default CoreActionExecutionMode executionMode() {
        return CoreActionExecutionMode.SYNC;
    }

    /** {@return timeout in milliseconds for this action} */
    default long timeoutMillis() {
        return DEFAULT_TIMEOUT_MILLIS;
    }

    /**
     * Optional custom validation after CoreLib has applied its base parameter checks.
     *
     * @param arguments resolved string arguments
     * @return validation result
     */
    @NotNull
    default CoreActionResult validate(@NotNull Map<String, String> arguments) {
        return CoreActionResult.ok();
    }

    /**
     * Executes the action.
     *
     * @param context execution context supplied by CoreLib
     * @param arguments resolved string arguments
     * @return action result
     */
    @NotNull
    CoreActionResult execute(@NotNull CoreActionContext context, @NotNull Map<String, String> arguments);
}
