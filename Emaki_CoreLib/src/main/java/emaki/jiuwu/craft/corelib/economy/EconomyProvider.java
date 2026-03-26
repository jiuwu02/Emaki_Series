package emaki.jiuwu.craft.corelib.economy;

import emaki.jiuwu.craft.corelib.operation.OperationResult;
import org.bukkit.entity.Player;

public interface EconomyProvider {

    String id();

    boolean isAvailable();

    double getBalance(Player player, String currencyId);

    OperationResult add(Player player, String currencyId, double amount);

    OperationResult remove(Player player, String currencyId, double amount);

    OperationResult set(Player player, String currencyId, double amount);
}
