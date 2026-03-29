package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;

abstract class AbstractCommandAction extends BaseAction {

    AbstractCommandAction(String id, String description) {
        super(id, "command", description, ActionParameter.required("command", ActionParameterType.STRING, "Command"));
    }

    protected String command(java.util.Map<String, String> arguments) {
        return ActionParsers.stripLeadingSlash(arguments.get("command"));
    }
}
