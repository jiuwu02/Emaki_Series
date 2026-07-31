package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;

/** Removes money from the target's balance. See {@link MoneyStage} for the shared contract. */
public final class TakeMoneyStage extends MoneyStage {

    public TakeMoneyStage(EconomyManager economyManager) {
        super("take_money", "Removes money from the target's balance.", economyManager);
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
