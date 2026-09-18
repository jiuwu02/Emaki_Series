package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger.OperationType;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;

public final class TakeMoneyStage extends MoneyStage {

    public TakeMoneyStage(EconomyManager economyManager, ActionAuditLogger auditLogger) {
        super("take_money", "Removes money from the target's balance.", economyManager,
                auditLogger, OperationType.DECREASE, false);
    }

    @Override
    ActionResult perform(EconomyManager economy,
            Player target,
            String provider,
            String currency,
            double amount) {
        return economy.remove(target, provider, currency, amount);
    }
}
