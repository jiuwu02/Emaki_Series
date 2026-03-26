package emaki.jiuwu.craft.corelib.economy;

import emaki.jiuwu.craft.corelib.operation.OperationErrorType;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;

public final class CoinsEngineEconomyProvider implements EconomyProvider {

    private final Plugin plugin;

    public CoinsEngineEconomyProvider(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "coinsengine";
    }

    @Override
    public boolean isAvailable() {
        return plugin != null && plugin.getServer().getPluginManager().isPluginEnabled("CoinsEngine") && CoinsEngineAPI.isLoaded();
    }

    @Override
    public double getBalance(Player player, String currencyId) {
        if (player == null) {
            return 0D;
        }
        ExcellentCurrency currency = resolveCurrency(currencyId);
        return currency == null ? 0D : CoinsEngineAPI.getBalance(player.getUniqueId(), currency);
    }

    @Override
    public OperationResult add(Player player, String currencyId, double amount) {
        if (player == null) {
            return unavailable();
        }
        ExcellentCurrency currency = resolveCurrency(currencyId);
        if (currency == null) {
            return missingCurrency(currencyId);
        }
        return CoinsEngineAPI.addBalance(player.getUniqueId(), currency, amount)
            ? OperationResult.ok()
            : OperationResult.failure(OperationErrorType.EXECUTION_EXCEPTION, "Failed to add CoinsEngine balance.");
    }

    @Override
    public OperationResult remove(Player player, String currencyId, double amount) {
        if (player == null) {
            return unavailable();
        }
        ExcellentCurrency currency = resolveCurrency(currencyId);
        if (currency == null) {
            return missingCurrency(currencyId);
        }
        double balance = CoinsEngineAPI.getBalance(player.getUniqueId(), currency);
        if (balance < amount) {
            return OperationResult.failure(OperationErrorType.INSUFFICIENT_BALANCE, "Insufficient CoinsEngine balance for currency '" + currencyId + "'.");
        }
        return CoinsEngineAPI.removeBalance(player.getUniqueId(), currency, amount)
            ? OperationResult.ok()
            : OperationResult.failure(OperationErrorType.EXECUTION_EXCEPTION, "Failed to remove CoinsEngine balance.");
    }

    @Override
    public OperationResult set(Player player, String currencyId, double amount) {
        if (player == null) {
            return unavailable();
        }
        ExcellentCurrency currency = resolveCurrency(currencyId);
        if (currency == null) {
            return missingCurrency(currencyId);
        }
        return CoinsEngineAPI.setBalance(player.getUniqueId(), currency, amount)
            ? OperationResult.ok()
            : OperationResult.failure(OperationErrorType.EXECUTION_EXCEPTION, "Failed to set CoinsEngine balance.");
    }

    private ExcellentCurrency resolveCurrency(String currencyId) {
        if (!isAvailable() || Texts.isBlank(currencyId)) {
            return null;
        }
        return CoinsEngineAPI.getCurrency(currencyId);
    }

    private OperationResult unavailable() {
        return OperationResult.failure(OperationErrorType.PROVIDER_UNAVAILABLE, "CoinsEngine provider is unavailable.");
    }

    private OperationResult missingCurrency(String currencyId) {
        return OperationResult.failure(OperationErrorType.CURRENCY_NOT_FOUND, "CoinsEngine currency not found: " + currencyId);
    }
}
