package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;

public final class SetMoneyOperation extends AbstractMoneyOperation {

    public SetMoneyOperation(EconomyManager economyManager) {
        super("set_money", economyManager, "Set money.");
    }

    @Override
    protected OperationResult perform(OperationContext context, Map<String, String> arguments) {
        return economyManager.set(context.player(), provider(arguments), currency(arguments), amount(arguments));
    }
}
