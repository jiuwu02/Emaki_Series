package emaki.jiuwu.craft.skills.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


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
    default List<SkillActionParameter> parameters() {
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

    /** {@return how this action is scheduled; defaults to {@link SkillActionExecutionMode#SYNC}} */
    default SkillActionExecutionMode executionMode() {
        return SkillActionExecutionMode.SYNC;
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
     * @return {@link SkillActionResult#ok()} when valid, otherwise a failure result
     */
    default SkillActionResult validate(Map<String, String> arguments) {
        Map<String, String> safeArguments = arguments == null ? Map.of() : arguments;
        for (SkillActionParameter parameter : parameters()) {
            String value = safeArguments.get(parameter.name());
            if (isBlank(value)) {
                if (parameter.required() && isBlank(parameter.defaultValue())) {
                    return SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT,
                            "Missing required argument '" + parameter.name() + "' for skill action '" + id() + "'.");
                }
                continue;
            }
            if (!parameter.type().isValid(value)) {
                return SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT,
                        "Invalid value for argument '" + parameter.name() + "' in skill action '" + id() + "': " + value);
            }
        }
        return SkillActionResult.ok();
    }

    /**
     * Executes the action.
     *
     * @param context   the skill-script execution context (caster, target,
     *                  variables, shared state)
     * @param arguments the resolved argument map
     * @return a future completing with the action's {@link SkillActionResult}
     */
    CompletableFuture<SkillActionResult> execute(SkillScriptContext context, Map<String, String> arguments);

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
