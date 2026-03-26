package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.Operation;
import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationErrorType;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

abstract class BaseOperation implements Operation {

    private final String id;
    private final String category;
    private final String description;
    private final List<OperationParameter> parameters;

    BaseOperation(String id, String category, String description, OperationParameter... parameters) {
        this.id = id;
        this.category = category;
        this.description = description;
        this.parameters = parameters == null ? List.of() : List.of(parameters);
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String category() {
        return category;
    }

    @Override
    public final String description() {
        return description;
    }

    @Override
    public final List<OperationParameter> parameters() {
        return parameters;
    }

    protected Player requirePlayer(OperationContext context) {
        return context == null ? null : context.player();
    }

    protected String stringArg(Map<String, String> arguments, String key) {
        return Texts.toStringSafe(arguments.get(key));
    }

    protected Map<String, String> applyDefaults(Map<String, String> arguments) {
        Map<String, String> copy = new LinkedHashMap<>(arguments);
        for (OperationParameter parameter : parameters) {
            if (Texts.isBlank(copy.get(parameter.name())) && Texts.isNotBlank(parameter.defaultValue())) {
                copy.put(parameter.name(), parameter.defaultValue());
            }
        }
        return copy;
    }

    protected OperationResult requirePlayerResult(OperationContext context) {
        return requirePlayer(context) == null
            ? OperationResult.failure(OperationErrorType.INVALID_STATE, "Operation '" + id() + "' requires a player context.")
            : OperationResult.ok();
    }
}
