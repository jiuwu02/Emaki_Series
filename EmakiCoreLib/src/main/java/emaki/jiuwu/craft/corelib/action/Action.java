package emaki.jiuwu.craft.corelib.action;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.Set;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.text.Texts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Action {

    long DEFAULT_TIMEOUT_MILLIS = 30_000L;
    Pattern PLACEHOLDER_PATTERN = Pattern.compile("%[a-zA-Z0-9_]+%");

    @NotNull
    String id();

    @NotNull
    String description();

    @NotNull
    String category();

    @NotNull
    default String version() {
        return "1.0.0";
    }

    @NotNull
    default List<ActionParameter> parameters() {
        return List.of();
    }

    default boolean acceptsDynamicParameter(@Nullable String name) {
        return false;
    }

    @NotNull
    default ActionExecutionMode executionMode() {
        return ActionExecutionMode.SYNC;
    }

    /**
     * Selects the concrete ownership domain for one invocation.
     *
     * <p>Bundled actions default to the contextual entity when a player is present,
     * or the server-global domain otherwise. Public third-party actions are adapted
     * separately and fail closed on Folia until they explicitly declare a target.</p>
     */
    default ActionExecutionTarget executionTarget(ActionPlanningContext context) {
        if (executionMode() == ActionExecutionMode.ASYNC_IO) {
            return ActionExecutionTarget.async();
        }
        return contextualTarget(context == null ? null : context.actionContext());
    }

    static ActionExecutionTarget contextualTarget(ActionContext context) {
        return context != null && context.player() != null
                ? ActionExecutionTarget.entity(context.player())
                : ActionExecutionTarget.global();
    }

    default long timeoutMillis() {
        return DEFAULT_TIMEOUT_MILLIS;
    }

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

    @NotNull
    default CompletionStage<ActionResult> validateAsync(@NotNull Map<String, String> arguments) {
        return CompletableFuture.completedFuture(validate(arguments));
    }

    @NotNull
    ActionResult execute(@NotNull ActionContext context, @NotNull Map<String, String> arguments);

    @NotNull
    default CompletionStage<ActionResult> executeAsync(@NotNull ActionContext context,
            @NotNull Map<String, String> arguments) {
        return CompletableFuture.completedFuture(execute(context, arguments));
    }
}
