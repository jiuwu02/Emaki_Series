package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import java.util.Map;

public final class SendMessageAction extends BaseAction {

    public SendMessageAction() {
        super("send_message", "message", "Send a MiniMessage chat message.", ActionParameter.required("text", ActionParameterType.STRING, "Message text"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        context.player().sendMessage(MiniMessages.parse(stringArg(arguments, "text")));
        return ActionResult.ok();
    }
}
