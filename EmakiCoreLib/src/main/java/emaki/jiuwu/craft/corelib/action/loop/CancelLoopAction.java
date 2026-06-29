package emaki.jiuwu.craft.corelib.action.loop;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class CancelLoopAction implements Action {

    private final LoopActionService service;

    public CancelLoopAction(LoopActionService service) {
        this.service = service;
    }

    @Override
    public @NotNull String id() {
        return "cancelloop";
    }

    @Override
    public @NotNull String description() {
        return "Cancel active loop tasks by key.";
    }

    @Override
    public @NotNull String category() {
        return "loop";
    }

    @Override
    public @NotNull List<ActionParameter> parameters() {
        return List.of(
                ActionParameter.required("key", ActionParameterType.STRING, "Loop key."),
                ActionParameter.optional("match", ActionParameterType.STRING, "exact", "exact or prefix."),
                ActionParameter.optional("silent", ActionParameterType.BOOLEAN, "true", "Treat missing loops as success.")
        );
    }

    @Override
    public @NotNull ActionResult execute(@NotNull ActionContext context, @NotNull Map<String, String> arguments) {
        Boolean silent = ActionParsers.parseBoolean(arguments.get("silent"));
        return service.cancel(arguments.get("key"), arguments.getOrDefault("match", "exact"), silent == null || silent);
    }
}
