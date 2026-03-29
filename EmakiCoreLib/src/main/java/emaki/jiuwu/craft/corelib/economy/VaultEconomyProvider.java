package emaki.jiuwu.craft.corelib.economy;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultEconomyProvider implements EconomyProvider {

    private final Plugin plugin;

    public VaultEconomyProvider(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "vault";
    }

    @Override
    public boolean isAvailable() {
        return economy() != null;
    }

    @Override
    public double getBalance(Player player, String currencyId) {
        Economy economy = economy();
        return player == null || economy == null ? 0D : economy.getBalance(player);
    }

    @Override
    public ActionResult add(Player player, String currencyId, double amount) {
        Economy economy = economy();
        if (player == null || economy == null) {
            return unavailable();
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess()
            ? ActionResult.ok()
            : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, response.errorMessage);
    }

    @Override
    public ActionResult remove(Player player, String currencyId, double amount) {
        Economy economy = economy();
        if (player == null || economy == null) {
            return unavailable();
        }
        double balance = economy.getBalance(player);
        if (balance < amount) {
            return ActionResult.failure(ActionErrorType.INSUFFICIENT_BALANCE, "Insufficient Vault balance.");
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess()
            ? ActionResult.ok()
            : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, response.errorMessage);
    }

    @Override
    public ActionResult set(Player player, String currencyId, double amount) {
        Economy economy = economy();
        if (player == null || economy == null) {
            return unavailable();
        }
        double current = economy.getBalance(player);
        if (current == amount) {
            return ActionResult.ok();
        }
        if (current < amount) {
            return add(player, currencyId, amount - current);
        }
        return remove(player, currencyId, current - amount);
    }

    private Economy economy() {
        if (plugin == null || plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    private ActionResult unavailable() {
        return ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "Vault economy provider is unavailable.");
    }
}
