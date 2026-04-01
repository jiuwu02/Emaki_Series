package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class HealAction extends BaseAction {

    public HealAction() {
        super("heal", "player", "Heal player.", ActionParameter.required("amount", ActionParameterType.DOUBLE, "Amount"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        double amount = ActionParsers.parseDouble(arguments.get("amount"), 0D);
        AttributeInstance attribute = context.player().getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? context.player().getHealth() : attribute.getValue();
        context.player().setHealth(Math.min(max, context.player().getHealth() + amount));
        return ActionResult.ok();
    }
}
