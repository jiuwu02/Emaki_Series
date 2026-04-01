package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;

public final class SetMoneyAction extends AbstractMoneyAction {

    public SetMoneyAction(EconomyManager economyManager) {
        super("setmoney", economyManager, "Set money.");
    }

    @Override
    protected ActionResult perform(ActionContext context, Map<String, String> arguments) {
        return economyManager.set(context.player(), provider(arguments), currency(arguments), amount(arguments));
    }
}
