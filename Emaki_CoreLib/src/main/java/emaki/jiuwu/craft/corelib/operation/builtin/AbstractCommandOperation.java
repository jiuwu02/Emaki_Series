package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationParsers;

abstract class AbstractCommandOperation extends BaseOperation {

    AbstractCommandOperation(String id, String description) {
        super(id, "command", description, OperationParameter.required("command", OperationParameterType.STRING, "Command"));
    }

    protected String command(java.util.Map<String, String> arguments) {
        return OperationParsers.stripLeadingSlash(arguments.get("command"));
    }
}
