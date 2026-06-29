package emaki.jiuwu.craft.corelib.action.loop;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class LoopSyncAction implements Action {

    private final LoopActionService service;

    public LoopSyncAction(LoopActionService service) {
        this.service = service;
    }

    @Override
    public @NotNull String id() {
        return "loopsync";
    }

    @Override
    public @NotNull String description() {
        return "Start a synchronous repeating CoreLib action template.";
    }

    @Override
    public @NotNull String category() {
        return "loop";
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
        return service.start(context, arguments, false);
    }
}

final class LoopActionParameters {

    private LoopActionParameters() {
    }

    static List<ActionParameter> startParameters() {
        return List.of(
                ActionParameter.required("template", ActionParameterType.STRING, "Action template id."),
                ActionParameter.required("times", ActionParameterType.INTEGER, "Loop execution count."),
                ActionParameter.required("interval", ActionParameterType.TIME, "Loop interval."),
                ActionParameter.optional("initial_delay", ActionParameterType.TIME, "0t", "Initial delay."),
                ActionParameter.optional("key", ActionParameterType.STRING, "", "Loop key."),
                ActionParameter.optional("mode", ActionParameterType.STRING, "replace", "replace, refresh, ignore, allow_duplicate."),
                ActionParameter.optional("stop_if_offline", ActionParameterType.BOOLEAN, "true", "Stop when player is offline."),
                ActionParameter.optional("stop_if_dead", ActionParameterType.BOOLEAN, "false", "Stop when player is dead."),
                ActionParameter.optional("stop_if_condition", ActionParameterType.STRING, "", "Stop when condition is not met."),
                ActionParameter.optional("stop_on_failure", ActionParameterType.BOOLEAN, "false", "Stop when template execution fails.")
        );
    }
}
