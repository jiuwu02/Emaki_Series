package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;

public final class GiveMoneyOperation extends AbstractMoneyOperation {

    public GiveMoneyOperation(EconomyManager economyManager) {
        super("give_money", economyManager, "Give money.");
    }

    @Override
    protected OperationResult perform(OperationContext context, Map<String, String> arguments) {
        return economyManager.add(context.player(), provider(arguments), currency(arguments), amount(arguments));
    }
}
