package emaki.jiuwu.craft.corelib.economy;

import emaki.jiuwu.craft.corelib.operation.OperationErrorType;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
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

    public OperationResult requireSupported(String providerId, String currencyId) {
        if ("coinsengine".equalsIgnoreCase(providerId) && Texts.isBlank(currencyId)) {
            return OperationResult.failure(OperationErrorType.INVALID_ARGUMENT, "CoinsEngine operations require 'currency'.");
        }
        EconomyProvider provider = select(providerId, currencyId);
        if (provider == null) {
            return OperationResult.failure(OperationErrorType.PROVIDER_UNAVAILABLE, "No economy provider available for '" + providerId + "'.");
        }
        if ("auto".equalsIgnoreCase(providerId) && Texts.isBlank(currencyId) && "coinsengine".equalsIgnoreCase(provider.id())) {
            return OperationResult.failure(OperationErrorType.PROVIDER_UNAVAILABLE, "Auto provider does not infer a default CoinsEngine currency.");
        }
        return OperationResult.ok(Map.of("provider", provider.id()));
    }

    public double getBalance(Player player, String providerId, String currencyId) {
        EconomyProvider provider = select(providerId, currencyId);
        return provider == null ? 0D : provider.getBalance(player, currencyId);
    }

    public OperationResult add(Player player, String providerId, String currencyId, double amount) {
        OperationResult supported = requireSupported(providerId, currencyId);
        if (!supported.success()) {
            return supported;
        }
        return select(providerId, currencyId).add(player, currencyId, amount);
    }

    public OperationResult remove(Player player, String providerId, String currencyId, double amount) {
        OperationResult supported = requireSupported(providerId, currencyId);
        if (!supported.success()) {
            return supported;
        }
        return select(providerId, currencyId).remove(player, currencyId, amount);
    }

    public OperationResult set(Player player, String providerId, String currencyId, double amount) {
        OperationResult supported = requireSupported(providerId, currencyId);
        if (!supported.success()) {
            return supported;
        }
        return select(providerId, currencyId).set(player, currencyId, amount);
    }
}
