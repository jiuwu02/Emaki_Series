package emaki.jiuwu.craft.corelib.action.loop;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutionMode;
import emaki.jiuwu.craft.corelib.action.ActionExecutionTarget;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionPlanningContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class LoopAsyncAction implements Action {

    private final LoopActionService service;

    public LoopAsyncAction(LoopActionService service) {
        this.service = service;
    }

    @Override
    public @NotNull String id() {
        return "loopasync";
    }

    @Override
    public @NotNull String description() {
        return "Start an async-safe repeating CoreLib action template.";
    }

    @Override
    public @NotNull String category() {
        return "loop";
    }

    @Override
    public @NotNull ActionExecutionMode executionMode() {
        return ActionExecutionMode.ASYNC_IO;
    }

    @Override
    public @NotNull ActionExecutionTarget executionTarget(ActionPlanningContext context) {
        return Action.contextualTarget(context == null ? null : context.actionContext());
    }

    @Override
    public @NotNull List<ActionParameter> parameters() {
        return LoopActionParameters.startParameters();
    }

    @Override
    public boolean acceptsDynamicParameter(String name) {
        return name != null && name.startsWith("with.");
    }

    @Override
    public @NotNull ActionResult execute(@NotNull ActionContext context, @NotNull Map<String, String> arguments) {
        return service.start(context, arguments, true);
    }
}
