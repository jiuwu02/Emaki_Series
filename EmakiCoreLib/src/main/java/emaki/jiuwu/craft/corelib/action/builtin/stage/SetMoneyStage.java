package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger.OperationType;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;

public final class SetMoneyStage extends MoneyStage {

    public SetMoneyStage(EconomyManager economyManager, ActionAuditLogger auditLogger) {
        super("set_money", "Sets the target's balance to an absolute value.", economyManager,
                auditLogger, OperationType.SET, true);
    }

    @Override
    ActionResult perform(EconomyManager economy,
            Player target,
            String provider,
            String currency,
            double amount) {
        return economy.set(target, provider, currency, amount);
    }
}
