package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import java.util.Map;

public final class GiveCoinsAction extends BaseAction {

    private final EconomyManager economyManager;

    public GiveCoinsAction(EconomyManager economyManager) {
        super(
            "givecoins",
            "economy",
            "Give CoinsEngine currency.",
            ActionParameter.required("amount", ActionParameterType.DOUBLE, "Amount"),
            ActionParameter.required("currency", ActionParameterType.STRING, "Currency")
        );
        this.economyManager = economyManager;
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        return economyManager.add(
            context.player(),
            "coinsengine",
            stringArg(arguments, "currency"),
            ActionParsers.parseDouble(arguments.get("amount"), 0D)
        );
    }
}
