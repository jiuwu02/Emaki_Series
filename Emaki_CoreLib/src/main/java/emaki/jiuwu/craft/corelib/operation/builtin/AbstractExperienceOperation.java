package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationParsers;

abstract class AbstractExperienceOperation extends BaseOperation {

    AbstractExperienceOperation(String id, String description) {
        super(
            id,
            "player",
            description,
            OperationParameter.required("amount", OperationParameterType.INTEGER, "Amount"),
            OperationParameter.optional("mode", OperationParameterType.STRING, "points", "points or levels")
        );
    }

    protected int amount(java.util.Map<String, String> arguments) {
        return Math.max(0, OperationParsers.parseInt(arguments.get("amount"), 0));
    }

    protected boolean levels(java.util.Map<String, String> arguments) {
        return "levels".equalsIgnoreCase(arguments.get("mode"));
    }
}
