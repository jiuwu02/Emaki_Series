package emaki.jiuwu.craft.corelib.economy;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class EconomyManager {

    private final Map<String, EconomyProvider> providers = new LinkedHashMap<>();

    public EconomyManager(Plugin plugin) {
        register(new VaultEconomyProvider(plugin));
        register(new CoinsEngineEconomyProvider(plugin));
    }

    public void register(EconomyProvider provider) {
        if (provider != null) {
            providers.put(Texts.lower(provider.id()), provider);
        }
    }

    public EconomyProvider get(String id) {
        return providers.get(Texts.lower(id));
    }

    public EconomyProvider select(String providerId, String currencyId) {
        String normalized = Texts.lower(providerId);
        if (Texts.isBlank(normalized) || "auto".equals(normalized)) {
            if (Texts.isNotBlank(currencyId)) {
                EconomyProvider coinsEngine = get("coinsengine");
                return coinsEngine != null && coinsEngine.isAvailable() ? coinsEngine : null;
            }
            EconomyProvider vault = get("vault");
            return vault != null && vault.isAvailable() ? vault : null;
        }
        EconomyProvider provider = get(normalized);
        return provider != null && provider.isAvailable() ? provider : null;
    }

    public ActionResult requireSupported(String providerId, String currencyId) {
        if ("coinsengine".equalsIgnoreCase(providerId) && Texts.isBlank(currencyId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "CoinsEngine operations require 'currency'.");
        }
        EconomyProvider provider = select(providerId, currencyId);
        if (provider == null) {
            return ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "No economy provider available for '" + providerId + "'.");
        }
        if ("auto".equalsIgnoreCase(providerId) && Texts.isBlank(currencyId) && "coinsengine".equalsIgnoreCase(provider.id())) {
            return ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "Auto provider does not infer a default CoinsEngine currency.");
        }
        return ActionResult.ok(Map.of("provider", provider.id()));
    }

    public double getBalance(Player player, String providerId, String currencyId) {
        EconomyProvider provider = select(providerId, currencyId);
        return provider == null ? 0D : provider.getBalance(player, currencyId);
    }

    public ActionResult add(Player player, String providerId, String currencyId, double amount) {
        ActionResult supported = requireSupported(providerId, currencyId);
        if (!supported.success()) {
            return supported;
        }
        return select(providerId, currencyId).add(player, currencyId, amount);
    }

    public ActionResult remove(Player player, String providerId, String currencyId, double amount) {
        ActionResult supported = requireSupported(providerId, currencyId);
        if (!supported.success()) {
            return supported;
        }
        return select(providerId, currencyId).remove(player, currencyId, amount);
    }

    public ActionResult set(Player player, String providerId, String currencyId, double amount) {
        ActionResult supported = requireSupported(providerId, currencyId);
        if (!supported.success()) {
            return supported;
        }
        return select(providerId, currencyId).set(player, currencyId, amount);
    }
}
