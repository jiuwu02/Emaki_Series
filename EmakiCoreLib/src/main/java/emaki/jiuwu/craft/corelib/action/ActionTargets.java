package emaki.jiuwu.craft.corelib.action;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;




public final class ActionTargets {

    @FunctionalInterface
    public interface Resolver {
        ActionExecutionTarget resolve(ActionPlanningContext context);
    }

    private ActionTargets() {
    }

    public static Action global(Action action) {
        return targeted(action, _ -> ActionExecutionTarget.global());
    }

    public static Action contextual(Action action) {
        return targeted(action, context -> Action.contextualTarget(context == null ? null : context.actionContext()));
    }

    public static Action location(Action action, Resolver resolver) {
        return targeted(action, resolver);
    }

    public static Action async(Action action) {
        return targeted(action, _ -> ActionExecutionTarget.async());
    }

    public static Action targeted(Action action, Resolver resolver) {
        if (action == null) {
            return null;
        }
        return new TargetedAction(action, resolver);
    }

    private record TargetedAction(Action delegate, Resolver resolver) implements Action {

        @Override
        public String id() {
            return delegate.id();
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public String category() {
            return delegate.category();
        }

        @Override
        public String version() {
            return delegate.version();
        }

        @Override
        public List<ActionParameter> parameters() {
            return delegate.parameters();
        }

        @Override
        public boolean acceptsDynamicParameter(String name) {
            return delegate.acceptsDynamicParameter(name);
        }

        @Override
        public ActionExecutionMode executionMode() {
            return delegate.executionMode();
        }

        @Override
        public ActionExecutionTarget executionTarget(ActionPlanningContext context) {
            return resolver == null ? ActionExecutionTarget.undeclared() : resolver.resolve(context);
        }

        @Override
        public long timeoutMillis() {
            return delegate.timeoutMillis();
        }

        @Override
        public ActionResult validate(Map<String, String> arguments) {
            return delegate.validate(arguments);
        }

        @Override
        public CompletionStage<ActionResult> validateAsync(Map<String, String> arguments) {
            return delegate.validateAsync(arguments);
        }

        @Override
        public ActionResult execute(ActionContext context, Map<String, String> arguments) {
            return delegate.execute(context, arguments);
        }

        @Override
        public CompletionStage<ActionResult> executeAsync(ActionContext context, Map<String, String> arguments) {
            return delegate.executeAsync(context, arguments);
        }
    }
}
