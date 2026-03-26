package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import java.util.Map;
import org.bukkit.Bukkit;

public final class BroadcastMessageOperation extends BaseOperation {

    public BroadcastMessageOperation() {
        super("broadcast_message", "message", "Broadcast a MiniMessage chat message.", OperationParameter.required("text", OperationParameterType.STRING, "Message text"));
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        Bukkit.broadcast(MiniMessages.parse(stringArg(arguments, "text")));
        return OperationResult.ok();
    }
}
