package emaki.jiuwu.craft.corelib.economy;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;
import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;

public final class ExcellentEconomyProvider implements EconomyProvider {

    private final Plugin plugin;

    public ExcellentEconomyProvider(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "excellenteconomy";
    }

    @Override
    public boolean isAvailable() {
        ExcellentEconomyAPI api = api();
        return api != null && api.canPerformOperations();
    }

    @Override
    public double getBalance(Player player, String currencyId) {
        if (player == null) {
            return 0D;
        }
        ExcellentEconomyAPI api = api();
        ExcellentCurrency currency = resolveCurrency(currencyId);
        return api == null || currency == null ? 0D : api.getBalance(player, currency);
    }

    @Override
    public ActionResult add(Player player, String currencyId, double amount) {
        if (player == null) {
            return unavailable();
        }
        ExcellentCurrency currency = resolveCurrency(currencyId);
        if (currency == null) {
            return missingCurrency(currencyId);
        }
        ExcellentEconomyAPI api = api();
        if (api == null) {
            return unavailable();
        }
        return api.deposit(player, currency, amount)
                ? ActionResult.ok()
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Failed to add ExcellentEconomy balance.");
    }

    @Override
    public ActionResult remove(Player player, String currencyId, double amount) {
        if (player == null) {
            return unavailable();
        }
        ExcellentCurrency currency = resolveCurrency(currencyId);
        if (currency == null) {
            return missingCurrency(currencyId);
        }
        ExcellentEconomyAPI api = api();
        if (api == null) {
            return unavailable();
        }
        double balance = api.getBalance(player, currency);
        if (balance < amount) {
            return ActionResult.failure(ActionErrorType.INSUFFICIENT_BALANCE, "Insufficient ExcellentEconomy balance for currency '" + currencyId + "'.");
        }
        return api.withdraw(player, currency, amount)
                ? ActionResult.ok()
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Failed to remove ExcellentEconomy balance.");
    }

    @Override
    public ActionResult set(Player player, String currencyId, double amount) {
        if (player == null) {
            return unavailable();
        }
        ExcellentCurrency currency = resolveCurrency(currencyId);
        if (currency == null) {
            return missingCurrency(currencyId);
        }
        ExcellentEconomyAPI api = api();
        if (api == null) {
            return unavailable();
        }
        return api.setBalance(player, currency, amount)
                ? ActionResult.ok()
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Failed to set ExcellentEconomy balance.");
    }

    private ExcellentCurrency resolveCurrency(String currencyId) {
        ExcellentEconomyAPI api = api();
        if (api == null || Texts.isBlank(currencyId)) {
            return null;
        }
        return api.getCurrency(currencyId);
    }

    private ExcellentEconomyAPI api() {
        if (plugin == null) {
            return null;
        }
        RegisteredServiceProvider<ExcellentEconomyAPI> registration =
                plugin.getServer().getServicesManager().getRegistration(ExcellentEconomyAPI.class);
        return registration == null ? null : registration.getProvider();
    }

    private ActionResult unavailable() {
        return ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "ExcellentEconomy provider is unavailable.");
    }

    private ActionResult missingCurrency(String currencyId) {
        return ActionResult.failure(ActionErrorType.CURRENCY_NOT_FOUND, "ExcellentEconomy currency not found: " + currencyId);
    }
}
