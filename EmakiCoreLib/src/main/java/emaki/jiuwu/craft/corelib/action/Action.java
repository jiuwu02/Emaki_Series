package emaki.jiuwu.craft.corelib.action;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.text.Texts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a single executable action that can be referenced from action scripts.
 */
public interface Action {

    long DEFAULT_TIMEOUT_MILLIS = 30_000L;
    Pattern PLACEHOLDER_PATTERN = Pattern.compile("%[a-zA-Z0-9_]+%");

    /**
     * Returns the canonical action id used by script lines.
     *
     * @return the unique action id
     */
    @NotNull
    String id();

    /**
     * Returns a short human-readable description for editors and diagnostics.
     *
     * @return the action description
     */
    @NotNull
    String description();

    /**
     * Returns the category that this action belongs to.
     *
     * @return the action category
     */
    @NotNull
    String category();

    /**
     * Returns the action definition version.
     *
     * @return the action definition version
     */
    @NotNull
    default String version() {
        return "1.0.0";
    }

    /**
     * Returns the declared parameters accepted by this action.
     *
     * @return the ordered parameter list
     */
    @NotNull
    default List<ActionParameter> parameters() {
        return List.of();
    }

    /**
     * Returns whether an undeclared argument name is accepted at runtime.
     *
     * @param name the argument name to test
     * @return {@code true} when the argument can be accepted dynamically
     */
    default boolean acceptsDynamicParameter(@Nullable String name) {
        return false;
    }

    /**
     * Returns the preferred execution mode for this action.
     *
     * @return the execution mode
     */
    @NotNull
    default ActionExecutionMode executionMode() {
        return ActionExecutionMode.SYNC;
    }

    /**
     * Returns the timeout used for action execution.
     *
     * @return the timeout in milliseconds
     */
    default long timeoutMillis() {
        return DEFAULT_TIMEOUT_MILLIS;
    }

    /**
     * Validates parsed arguments before execution.
     *
     * @param arguments the resolved action arguments
     * @return the validation result
     */
    @NotNull
    default ActionResult validate(@NotNull Map<String, String> arguments) {
        Set<String> known = new LinkedHashSet<>();
        for (ActionParameter parameter : parameters()) {
            known.add(parameter.name());
            String value = arguments.get(parameter.name());
            if (Texts.isBlank(value)) {
                if (parameter.required() && Texts.isBlank(parameter.defaultValue())) {
                    return ActionResult.failure(
                            ActionErrorType.INVALID_ARGUMENT,
                            "Missing required argument '" + parameter.name() + "' for action '" + id() + "'."
                    );
                }
                continue;
            }
            if (PLACEHOLDER_PATTERN.matcher(value).find()) {
                continue;
            }
            if (!parameter.type().isValid(value)) {
                return ActionResult.failure(
                        ActionErrorType.INVALID_ARGUMENT,
                        "Invalid value for argument '" + parameter.name() + "' in action '" + id() + "': " + value
                );
            }
        }
        for (String argument : arguments.keySet()) {
            if (!known.contains(argument) && !acceptsDynamicParameter(argument)) {
                return ActionResult.failure(
                        ActionErrorType.INVALID_ARGUMENT,
                        "Unknown argument '" + argument + "' for action '" + id() + "'."
                );
            }
        }
        return ActionResult.ok();
    }

    /**
     * Executes the action with resolved arguments.
     *
     * @param context the runtime context for the current action step
     * @param arguments the resolved action arguments
     * @return the execution result
     */
    @NotNull
    ActionResult execute(@NotNull ActionContext context, @NotNull Map<String, String> arguments);
}
