package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionExecutionTarget;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionPlanningContext;

abstract class LocationTargetAction extends BaseAction {

    LocationTargetAction(String id,
            String category,
            String description,
            ActionParameter... parameters) {
        super(id, category, description, parameters);
    }

    @Override
    public ActionExecutionTarget executionTarget(ActionPlanningContext context) {
        if (context == null || context.actionContext() == null) {
            return ActionExecutionTarget.global();
        }
        ActionLocationResolver.ResolvedLocation resolved = ActionLocationResolver.resolve(
                context.actionContext(), context.arguments(), id());
        return resolved.success()
                ? ActionExecutionTarget.location(resolved.location())
                : super.executionTarget(context);
    }
}
