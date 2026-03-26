package emaki.jiuwu.craft.corelib.operation;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public interface Operation {

    Pattern PLACEHOLDER_PATTERN = Pattern.compile("%[a-zA-Z0-9_]+%");

    String id();

    String description();

    String category();

    default String version() {
        return "1.0.0";
    }

    default List<OperationParameter> parameters() {
        return List.of();
    }

    default boolean acceptsDynamicParameter(String name) {
        return false;
    }

    default OperationResult validate(Map<String, String> arguments) {
        Set<String> known = new LinkedHashSet<>();
        for (OperationParameter parameter : parameters()) {
            known.add(parameter.name());
            String value = arguments.get(parameter.name());
            if (Texts.isBlank(value)) {
                if (parameter.required() && Texts.isBlank(parameter.defaultValue())) {
                    return OperationResult.failure(
                        OperationErrorType.INVALID_ARGUMENT,
                        "Missing required argument '" + parameter.name() + "' for operation '" + id() + "'."
                    );
                }
                continue;
            }
            if (PLACEHOLDER_PATTERN.matcher(value).find()) {
                continue;
            }
            if (!parameter.type().isValid(value)) {
                return OperationResult.failure(
                    OperationErrorType.INVALID_ARGUMENT,
                    "Invalid value for argument '" + parameter.name() + "' in operation '" + id() + "': " + value
                );
            }
        }
        for (String argument : arguments.keySet()) {
            if (!known.contains(argument) && !acceptsDynamicParameter(argument)) {
                return OperationResult.failure(
                    OperationErrorType.INVALID_ARGUMENT,
                    "Unknown argument '" + argument + "' for operation '" + id() + "'."
                );
            }
        }
        return OperationResult.ok();
    }

    OperationResult execute(OperationContext context, Map<String, String> arguments);
}
