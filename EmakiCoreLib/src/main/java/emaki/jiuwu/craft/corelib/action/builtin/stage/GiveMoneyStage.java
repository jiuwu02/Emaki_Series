package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger.OperationType;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;

public final class GiveMoneyStage extends MoneyStage {

    public GiveMoneyStage(EconomyManager economyManager, ActionAuditLogger auditLogger) {
        super("give_money", "Adds money to the target's balance.", economyManager,
                auditLogger, OperationType.INCREASE, false);
    }

    @Override
    ActionResult perform(EconomyManager economy,
            Player target,
            String provider,
            String currency,
            double amount) {
        return economy.add(target, provider, currency, amount);
    }
}
