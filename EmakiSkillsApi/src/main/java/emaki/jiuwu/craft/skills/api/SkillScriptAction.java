package emaki.jiuwu.craft.skills.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionExecutionMode;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * A custom action that can be invoked from an EmakiSkills skill script.
 *
 * <p>Implement this interface and register it through
 * {@link SkillScriptActionRegistry} to expose new behavior (damage, movement,
 * effects, integrations, ...) to skill configurations. Each action declares its
 * id, parameters, execution mode and timeout, and performs its work in
 * {@link #execute(SkillScriptContext, Map)}.
 *
 * <p>Most members have sensible defaults; at minimum an implementation must
 * provide {@link #id()} and {@link #execute(SkillScriptContext, Map)}.
 */
public interface SkillScriptAction {

    /** {@return the unique id used to reference this action in scripts} */
    String id();

    /** {@return the grouping category for this action; defaults to {@code "skill"}} */
    default String category() {
        return "skill";
    }

    /** {@return a human-readable description; defaults to the action id} */
    default String description() {
        return id();
    }

    /** {@return the declared parameters of this action; empty by default} */
    default List<ActionParameter> parameters() {
        return List.of();
    }

    /**
     * {@return whether an undeclared parameter name is accepted} Override to
     * allow dynamic, schema-less arguments. Defaults to {@code false}.
     *
     * @param name the parameter name being probed
     */
    default boolean acceptsDynamicParameter(String name) {
        return false;
    }

    /** {@return how this action is scheduled; defaults to {@link ActionExecutionMode#SYNC}} */
    default ActionExecutionMode executionMode() {
        return ActionExecutionMode.SYNC;
    }

    /** {@return the execution timeout in milliseconds; defaults to 30000} */
    default long timeoutMillis() {
        return 30_000L;
    }

    /**
     * Validates the supplied arguments against {@link #parameters()}.
     *
     * <p>The default implementation checks for missing required parameters and
     * type validity. Override for custom validation.
     *
     * @param arguments the raw argument map; {@code null} is treated as empty
     * @return {@link ActionResult#ok()} when valid, otherwise a failure result
     */
    default ActionResult validate(Map<String, String> arguments) {
        Map<String, String> safeArguments = arguments == null ? Map.of() : arguments;
        for (ActionParameter parameter : parameters()) {
            String value = safeArguments.get(parameter.name());
            if (Texts.isBlank(value)) {
                if (parameter.required() && Texts.isBlank(parameter.defaultValue())) {
                    return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT,
                            "Missing required argument '" + parameter.name() + "' for skill action '" + id() + "'.");
                }
                continue;
            }
            if (!parameter.type().isValid(value)) {
                return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT,
                        "Invalid value for argument '" + parameter.name() + "' in skill action '" + id() + "': " + value);
            }
        }
        return ActionResult.ok();
    }

    /**
     * Executes the action.
     *
     * @param context   the skill-script execution context (caster, target,
     *                  variables, shared state)
     * @param arguments the resolved argument map
     * @return a future completing with the action's {@link ActionResult}
     */
    CompletableFuture<ActionResult> execute(SkillScriptContext context, Map<String, String> arguments);
}
