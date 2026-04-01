package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;

abstract class AbstractExperienceAction extends BaseAction {

    AbstractExperienceAction(String id, String description) {
        super(
                id,
                "player",
                description,
                ActionParameter.required("amount", ActionParameterType.INTEGER, "Amount"),
                ActionParameter.optional("mode", ActionParameterType.STRING, "points", "points or levels")
        );
    }

    protected int amount(java.util.Map<String, String> arguments) {
        return Math.max(0, ActionParsers.parseInt(arguments.get("amount"), 0));
    }

    protected boolean levels(java.util.Map<String, String> arguments) {
        return "levels".equalsIgnoreCase(arguments.get("mode"));
    }
}
