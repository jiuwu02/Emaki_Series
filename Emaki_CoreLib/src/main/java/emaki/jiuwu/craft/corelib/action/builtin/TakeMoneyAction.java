package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import java.util.Map;

public final class TakeMoneyAction extends AbstractMoneyAction {

    public TakeMoneyAction(EconomyManager economyManager) {
        super("take_money", economyManager, "Take money.");
    }

    @Override
    protected ActionResult perform(ActionContext context, Map<String, String> arguments) {
        return economyManager.remove(context.player(), provider(arguments), currency(arguments), amount(arguments));
    }
}
