package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;

/** Sets the target's balance to an absolute value. See {@link MoneyStage} for the shared contract. */
public final class SetMoneyStage extends MoneyStage {

    public SetMoneyStage(EconomyManager economyManager) {
        super("set_money", "Sets the target's balance to an absolute value.", economyManager);
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
