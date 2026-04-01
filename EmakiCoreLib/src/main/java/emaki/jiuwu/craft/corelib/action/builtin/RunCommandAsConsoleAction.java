package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.Bukkit;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class RunCommandAsConsoleAction extends AbstractCommandAction {

    public RunCommandAsConsoleAction() {
        super("runcommandasconsole", "Run command as console.");
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command(arguments))
                ? ActionResult.ok()
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Failed to dispatch console command.");
    }
}
