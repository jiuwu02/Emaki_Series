package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import java.util.Map;

public final class SendMessageOperation extends BaseOperation {

    public SendMessageOperation() {
        super("send_message", "message", "Send a MiniMessage chat message.", OperationParameter.required("text", OperationParameterType.STRING, "Message text"));
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        context.player().sendMessage(MiniMessages.parse(stringArg(arguments, "text")));
        return OperationResult.ok();
    }
}
