package emaki.jiuwu.craft.corelib.economy;

import emaki.jiuwu.craft.corelib.operation.OperationErrorType;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
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
    public OperationResult add(Player player, String currencyId, double amount) {
        Economy economy = economy();
        if (player == null || economy == null) {
            return unavailable();
        }
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess()
            ? OperationResult.ok()
            : OperationResult.failure(OperationErrorType.EXECUTION_EXCEPTION, response.errorMessage);
    }

    @Override
    public OperationResult remove(Player player, String currencyId, double amount) {
        Economy economy = economy();
        if (player == null || economy == null) {
            return unavailable();
        }
        double balance = economy.getBalance(player);
        if (balance < amount) {
            return OperationResult.failure(OperationErrorType.INSUFFICIENT_BALANCE, "Insufficient Vault balance.");
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess()
            ? OperationResult.ok()
            : OperationResult.failure(OperationErrorType.EXECUTION_EXCEPTION, response.errorMessage);
    }

    @Override
    public OperationResult set(Player player, String currencyId, double amount) {
        Economy economy = economy();
        if (player == null || economy == null) {
            return unavailable();
        }
        double current = economy.getBalance(player);
        if (current == amount) {
            return OperationResult.ok();
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

    private OperationResult unavailable() {
        return OperationResult.failure(OperationErrorType.PROVIDER_UNAVAILABLE, "Vault economy provider is unavailable.");
    }
}
