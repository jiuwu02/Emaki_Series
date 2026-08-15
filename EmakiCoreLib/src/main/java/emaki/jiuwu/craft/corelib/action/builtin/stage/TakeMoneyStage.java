package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;

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
