package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import java.util.Map;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

public final class SetHealthAction extends BaseAction {

    public SetHealthAction() {
        super("sethealth", "player", "Set player health.", ActionParameter.required("amount", ActionParameterType.DOUBLE, "Amount"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        double amount = ActionParsers.parseDouble(arguments.get("amount"), context.player().getHealth());
        AttributeInstance attribute = context.player().getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? context.player().getHealth() : attribute.getValue();
        context.player().setHealth(Math.max(0D, Math.min(max, amount)));
        return ActionResult.ok();
    }
}
