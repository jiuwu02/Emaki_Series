package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationErrorType;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;
import org.bukkit.Bukkit;

public final class RunCommandAsConsoleOperation extends AbstractCommandOperation {

    public RunCommandAsConsoleOperation() {
        super("run_command_as_console", "Run command as console.");
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command(arguments))
            ? OperationResult.ok()
            : OperationResult.failure(OperationErrorType.EXECUTION_EXCEPTION, "Failed to dispatch console command.");
    }
}
