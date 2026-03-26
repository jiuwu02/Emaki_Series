package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;
import org.bukkit.Bukkit;

public final class RunCommandAsPlayerOperation extends AbstractCommandOperation {

    public RunCommandAsPlayerOperation() {
        super("run_command_as_player", "Run command as player.");
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        return Bukkit.dispatchCommand(context.player(), command(arguments))
            ? OperationResult.ok()
            : OperationResult.failure(emaki.jiuwu.craft.corelib.operation.OperationErrorType.EXECUTION_EXCEPTION, "Failed to dispatch player command.");
    }
}
