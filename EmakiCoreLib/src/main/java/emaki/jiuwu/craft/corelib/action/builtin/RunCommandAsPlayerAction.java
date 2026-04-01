package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.Bukkit;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class RunCommandAsPlayerAction extends AbstractCommandAction {

    public RunCommandAsPlayerAction() {
        super("runcommandasplayer", "Run command as player.");
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        return Bukkit.dispatchCommand(context.player(), command(arguments))
                ? ActionResult.ok()
                : ActionResult.failure(emaki.jiuwu.craft.corelib.action.ActionErrorType.EXECUTION_EXCEPTION, "Failed to dispatch player command.");
    }
}
