package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;

public final class TakeMoneyOperation extends AbstractMoneyOperation {

    public TakeMoneyOperation(EconomyManager economyManager) {
        super("take_money", economyManager, "Take money.");
    }

    @Override
    protected OperationResult perform(OperationContext context, Map<String, String> arguments) {
        return economyManager.remove(context.player(), provider(arguments), currency(arguments), amount(arguments));
    }
}
