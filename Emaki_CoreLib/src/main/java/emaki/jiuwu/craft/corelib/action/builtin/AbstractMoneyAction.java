package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import java.util.Map;

abstract class AbstractMoneyAction extends BaseAction {

    protected final EconomyManager economyManager;

    AbstractMoneyAction(String id, EconomyManager economyManager, String description) {
        super(
            id,
            "economy",
            description,
            ActionParameter.required("amount", ActionParameterType.DOUBLE, "Amount"),
            ActionParameter.optional("provider", ActionParameterType.STRING, "auto", "Provider"),
            ActionParameter.optional("currency", ActionParameterType.STRING, "", "Currency")
        );
        this.economyManager = economyManager;
    }

    protected double amount(Map<String, String> arguments) {
        return ActionParsers.parseDouble(arguments.get("amount"), 0D);
    }

    protected String provider(Map<String, String> arguments) {
        return stringArg(arguments, "provider");
    }

    protected String currency(Map<String, String> arguments) {
        return stringArg(arguments, "currency");
    }

    @Override
    public final ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        return perform(context, arguments);
    }

    protected abstract ActionResult perform(ActionContext context, Map<String, String> arguments);
}
