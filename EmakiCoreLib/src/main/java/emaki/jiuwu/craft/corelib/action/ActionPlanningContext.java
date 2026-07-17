package emaki.jiuwu.craft.corelib.action;

import java.util.Map;

/**
 * Inputs available while selecting the scheduler owner for an action.
 */
public record ActionPlanningContext(ActionContext actionContext, Map<String, String> arguments) {

    public ActionPlanningContext {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
