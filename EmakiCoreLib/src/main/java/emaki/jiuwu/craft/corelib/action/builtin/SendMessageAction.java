package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.MiniMessages;

public final class SendMessageAction extends BaseAction {

    public SendMessageAction() {
        super("sendmessage", "message", "Send a MiniMessage chat message.", ActionParameter.required("text", ActionParameterType.STRING, "Message text"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        AdventureSupport.sendMessage(context.sourcePlugin(), context.player(), MiniMessages.parse(stringArg(arguments, "text")));
        return ActionResult.ok();
    }
}
