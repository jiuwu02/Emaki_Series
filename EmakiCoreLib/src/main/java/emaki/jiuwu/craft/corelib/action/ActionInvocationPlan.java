package emaki.jiuwu.craft.corelib.action;

import java.util.Map;

record ActionInvocationPlan(
        RegisteredAction registration,
        ActionContext context,
        Map<String, String> arguments,
        ActionExecutionTarget target,
        ActionResult failure) {

    ActionInvocationPlan {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    static ActionInvocationPlan failure(RegisteredAction registration,
            ActionContext context,
            ActionResult failure) {
        return new ActionInvocationPlan(registration, context, Map.of(), null, failure);
    }

    boolean valid() {
        return failure == null && registration != null && target != null && target.valid();
    }
}
