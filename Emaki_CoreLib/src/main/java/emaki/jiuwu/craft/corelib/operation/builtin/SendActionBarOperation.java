package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import java.util.Map;

public final class SendActionBarOperation extends BaseOperation {

    public SendActionBarOperation() {
        super("send_actionbar", "message", "Send an action bar message.", OperationParameter.required("text", OperationParameterType.STRING, "Action bar text"));
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        context.player().sendActionBar(MiniMessages.parse(stringArg(arguments, "text")));
        return OperationResult.ok();
    }
}
