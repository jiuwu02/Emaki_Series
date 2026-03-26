package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import java.util.Map;

public final class GiveMoneyAction extends AbstractMoneyAction {

    public GiveMoneyAction(EconomyManager economyManager) {
        super("give_money", economyManager, "Give money.");
    }

    @Override
    protected ActionResult perform(ActionContext context, Map<String, String> arguments) {
        return economyManager.add(context.player(), provider(arguments), currency(arguments), amount(arguments));
    }
}
