package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class DamageAction extends BaseAction {

    public DamageAction() {
        super("damage", "player", "Damage player.", ActionParameter.required("amount", ActionParameterType.DOUBLE, "Amount"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        double amount = ActionParsers.parseDouble(arguments.get("amount"), 0D);
        context.player().setHealth(Math.max(0D, context.player().getHealth() - amount));
        return ActionResult.ok();
    }
}
