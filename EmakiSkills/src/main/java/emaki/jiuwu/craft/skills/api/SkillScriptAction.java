package emaki.jiuwu.craft.skills.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionExecutionMode;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.script.SkillScriptContext;

public interface SkillScriptAction {

    String id();

    default String category() {
        return "skill";
    }

    default String description() {
        return id();
    }

    default List<ActionParameter> parameters() {
        return List.of();
    }

    default boolean acceptsDynamicParameter(String name) {
        return false;
    }

    default ActionExecutionMode executionMode() {
        return ActionExecutionMode.SYNC;
    }

    default long timeoutMillis() {
        return 30_000L;
    }

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

    CompletableFuture<ActionResult> execute(SkillScriptContext context, Map<String, String> arguments);
}
