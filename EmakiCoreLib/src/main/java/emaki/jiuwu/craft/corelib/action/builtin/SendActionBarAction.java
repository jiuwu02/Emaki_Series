package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.AdventureSupport;
import emaki.jiuwu.craft.corelib.text.MiniMessages;

public final class SendActionBarAction extends BaseAction {

    public SendActionBarAction() {
        super("sendactionbar", "message", "Send an action bar message.", ActionParameter.required("text", ActionParameterType.STRING, "Action bar text"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        AdventureSupport.sendActionBar(context.sourcePlugin(), context.player(), MiniMessages.parse(stringArg(arguments, "text")));
        return ActionResult.ok();
    }
}
