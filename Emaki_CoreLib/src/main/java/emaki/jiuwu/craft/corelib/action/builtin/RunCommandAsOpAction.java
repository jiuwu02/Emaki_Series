package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import java.util.Map;
import org.bukkit.Bukkit;

public final class RunCommandAsOpAction extends AbstractCommandAction {

    public RunCommandAsOpAction() {
        super("run_command_as_op", "Run command as temporary operator.");
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        boolean original = context.player().isOp();
        try {
            if (!original) {
                context.player().setOp(true);
            }
            return Bukkit.dispatchCommand(context.player(), command(arguments))
                ? ActionResult.ok()
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Failed to dispatch OP command.");
        } finally {
            if (!original) {
                context.player().setOp(false);
            }
        }
    }
}
