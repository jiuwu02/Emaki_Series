package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationErrorType;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;
import org.bukkit.Bukkit;

public final class RunCommandAsOpOperation extends AbstractCommandOperation {

    public RunCommandAsOpOperation() {
        super("run_command_as_op", "Run command as temporary operator.");
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        boolean original = context.player().isOp();
        try {
            if (!original) {
                context.player().setOp(true);
            }
            return Bukkit.dispatchCommand(context.player(), command(arguments))
                ? OperationResult.ok()
                : OperationResult.failure(OperationErrorType.EXECUTION_EXCEPTION, "Failed to dispatch OP command.");
        } finally {
            if (!original) {
                context.player().setOp(false);
            }
        }
    }
}
