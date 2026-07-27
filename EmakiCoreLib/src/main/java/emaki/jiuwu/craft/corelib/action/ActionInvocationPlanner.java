package emaki.jiuwu.craft.corelib.action;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.text.Texts;

final class ActionInvocationPlanner {

    private final PlaceholderRegistry placeholderRegistry;
    private final ActionDispatchScheduler scheduler;

    ActionInvocationPlanner(PlaceholderRegistry placeholderRegistry, ActionDispatchScheduler scheduler) {
        this.placeholderRegistry = placeholderRegistry;
        this.scheduler = scheduler;
    }

    CompletableFuture<ActionInvocationPlan> plan(RegisteredAction registration,
            ActionContext context,
            Map<String, String> rawArguments) {
        if (registration == null) {
            return CompletableFuture.completedFuture(ActionInvocationPlan.failure(
                    null,
                    context,
                    ActionResult.failure(ActionErrorType.ACTION_NOT_FOUND, "Action registration is unavailable.")));
        }
        ActionExecutionTarget planningTarget = Action.contextualTarget(context);
        return scheduler.dispatch(
                registration.owner(),
                planningTarget,
                0L,
                "action-plan:" + registration.action().id(),
                () -> planOnOwnedDomain(registration, context, rawArguments));
    }

    private CompletionStage<ActionInvocationPlan> planOnOwnedDomain(RegisteredAction registration,
            ActionContext context,
            Map<String, String> rawArguments) {
        try {
            Action action = registration.action();
            Map<String, String> resolved = resolveArguments(context, rawArguments);
            ActionExecutionTarget target = action.executionTarget(new ActionPlanningContext(context, resolved));
            if (target == null || !target.valid()) {
                ActionResult failure = target == null || target.failure() == null
                        ? ActionResult.failure(ActionErrorType.INVALID_STATE,
                                "Action execution target could not be planned.")
                        : target.failure();
                return CompletableFuture.completedFuture(ActionInvocationPlan.failure(
                        registration, context, failure));
            }
            CompletionStage<ActionResult> validationStage = action.validateAsync(resolved);
            if (validationStage == null) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Action returned a null validation completion stage."));
            }
            return validationStage.thenApply(validation -> {
                ActionResult result = validation == null ? ActionResult.ok() : validation;
                return result.success()
                        ? new ActionInvocationPlan(registration, context, resolved, target, null)
                        : ActionInvocationPlan.failure(registration, context, result);
            });
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    private Map<String, String> resolveArguments(ActionContext context, Map<String, String> arguments) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (arguments == null) {
            return Map.of();
        }
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            String raw = entry.getValue();
            resolved.put(entry.getKey(), Texts.isBlank(raw) ? raw : placeholderRegistry.resolve(context, raw));
        }
        return Map.copyOf(resolved);
    }
}
