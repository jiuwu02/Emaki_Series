package emaki.jiuwu.craft.corelib.economy;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import su.nightexpress.coinsengine.CoinsEnginePlugin;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;
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
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Failed to add CoinsEngine balance.");
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
            return ActionResult.failure(ActionErrorType.INSUFFICIENT_BALANCE, "Insufficient CoinsEngine balance for currency '" + currencyId + "'.");
        }
        return api.withdraw(player, currency, amount)
                ? ActionResult.ok()
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Failed to remove CoinsEngine balance.");
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
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "Failed to set CoinsEngine balance.");
    }

    private ExcellentCurrency resolveCurrency(String currencyId) {
        ExcellentEconomyAPI api = api();
        if (api == null || Texts.isBlank(currencyId)) {
            return null;
        }
        return api.currencyById(currencyId).orElse(null);
    }

    private ExcellentEconomyAPI api() {
        CoinsEnginePlugin coinsEnginePlugin = coinsEnginePlugin();
        return coinsEnginePlugin == null ? null : coinsEnginePlugin.getAPI();
    }

    private CoinsEnginePlugin coinsEnginePlugin() {
        if (plugin == null) {
            return null;
        }
        Plugin external = plugin.getServer().getPluginManager().getPlugin("CoinsEngine");
        if (!(external instanceof CoinsEnginePlugin coinsEnginePlugin) || !external.isEnabled()) {
            return null;
        }
        return coinsEnginePlugin;
    }

    private ActionResult unavailable() {
        return ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "CoinsEngine provider is unavailable.");
    }

    private ActionResult missingCurrency(String currencyId) {
        return ActionResult.failure(ActionErrorType.CURRENCY_NOT_FOUND, "CoinsEngine currency not found: " + currencyId);
    }
}
