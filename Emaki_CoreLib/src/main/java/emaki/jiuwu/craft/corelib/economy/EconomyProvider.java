package emaki.jiuwu.craft.corelib.economy;

import emaki.jiuwu.craft.corelib.action.ActionResult;
import org.bukkit.entity.Player;

public interface EconomyProvider {

    String id();

    boolean isAvailable();

    double getBalance(Player player, String currencyId);

    ActionResult add(Player player, String currencyId, double amount);

    ActionResult remove(Player player, String currencyId, double amount);

    ActionResult set(Player player, String currencyId, double amount);
}
