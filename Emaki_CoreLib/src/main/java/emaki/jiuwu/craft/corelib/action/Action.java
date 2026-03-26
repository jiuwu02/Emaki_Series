package emaki.jiuwu.craft.corelib.action;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public interface Action {

    Pattern PLACEHOLDER_PATTERN = Pattern.compile("%[a-zA-Z0-9_]+%");

    String id();

    String description();

    String category();

    default String version() {
        return "1.0.0";
    }

    default List<ActionParameter> parameters() {
        return List.of();
    }

    default boolean acceptsDynamicParameter(String name) {
        return false;
    }

    default ActionResult validate(Map<String, String> arguments) {
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

    ActionResult execute(ActionContext context, Map<String, String> arguments);
}
