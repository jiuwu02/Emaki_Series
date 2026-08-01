package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;

/** Adds money to the target's balance. See {@link MoneyStage} for the shared contract. */
public final class GiveMoneyStage extends MoneyStage {

    public GiveMoneyStage(EconomyManager economyManager) {
        super("give_money", "Adds money to the target's balance.", economyManager);
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
