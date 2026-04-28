package emaki.jiuwu.craft.corelib.economy;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class EconomyManager {

    private final Map<String, EconomyProvider> providers = new LinkedHashMap<>();

    public EconomyManager(Plugin plugin) {
        registerOptionalProvider(plugin, "Vault", () -> new VaultEconomyProvider(plugin));
        registerOptionalProvider(plugin, "ExcellentEconomy", () -> new ExcellentEconomyProvider(plugin));
    }

    public void register(EconomyProvider provider) {
        if (provider != null) {
            providers.put(Texts.lower(provider.id()), provider);
        }
    }

    private void registerOptionalProvider(Plugin plugin, String dependencyName, Supplier<EconomyProvider> providerFactory) {
        if (!hasEnabledPlugin(plugin, dependencyName) || providerFactory == null) {
            return;
        }
        try {
            register(providerFactory.get());
        } catch (RuntimeException | LinkageError exception) {
        }
    }

    private boolean hasEnabledPlugin(Plugin plugin, String dependencyName) {
        if (plugin == null) {
            return false;
        }
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(dependencyName);
        return dependency != null && dependency.isEnabled();
    }

    public EconomyProvider get(String id) {
        return providers.get(Texts.lower(id));
    }

    public List<String> providerIds() {
        return providers.values().stream()
                .map(EconomyProvider::id)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<String> availableProviderIds() {
        return providers.values().stream()
                .filter(EconomyProvider::isAvailable)
                .map(EconomyProvider::id)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public EconomyProvider select(String providerId, String currencyId) {
        String normalized = Texts.lower(providerId);
        if (Texts.isBlank(normalized) || "auto".equals(normalized)) {
            if (Texts.isNotBlank(currencyId)) {
                EconomyProvider economy = get("excellenteconomy");
                return economy != null && economy.isAvailable() ? economy : null;
            }
            EconomyProvider vault = get("vault");
            return vault != null && vault.isAvailable() ? vault : null;
        }
        EconomyProvider provider = get(normalized);
        return provider != null && provider.isAvailable() ? provider : null;
    }

    public ActionResult requireSupported(String providerId, String currencyId) {
        if ("excellenteconomy".equalsIgnoreCase(providerId) && Texts.isBlank(currencyId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "ExcellentEconomy actions require 'currency'.");
        }
        EconomyProvider provider = select(providerId, currencyId);
        if (provider == null) {
            return ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "No economy provider available for '" + providerId + "'.");
        }
        if ("auto".equalsIgnoreCase(providerId) && Texts.isBlank(currencyId) && "excellenteconomy".equalsIgnoreCase(provider.id())) {
            return ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "Auto provider does not infer a default ExcellentEconomy currency.");
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
