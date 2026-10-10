package emaki.jiuwu.craft.corelib.economy;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.api.text.Texts;

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
            plugin.getLogger().log(Level.WARNING,
                    "[economy] 经济提供者注册失败: dependency=" + dependencyName
                            + ", operation=register_optional_provider, cause=" + exception,
                    exception);
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
        return resolveSupported(providerId, currencyId).result();
    }

    private Supported resolveSupported(String providerId, String currencyId) {
        if ("excellenteconomy".equalsIgnoreCase(providerId) && Texts.isBlank(currencyId)) {
            return new Supported(null,
                    ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "ExcellentEconomy 操作需要 'currency'。"));
        }
        EconomyProvider provider = select(providerId, currencyId);
        if (provider == null) {
            return new Supported(null,
                    ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "没有可用的经济提供者: '" + providerId + "'。"));
        }
        if ("auto".equalsIgnoreCase(providerId) && Texts.isBlank(currencyId) && "excellenteconomy".equalsIgnoreCase(provider.id())) {
            return new Supported(null,
                    ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "auto 提供者不会推断默认的 ExcellentEconomy 货币。"));
        }
        return new Supported(provider, ActionResult.ok(Map.of("provider", provider.id())));
    }

    public double getBalance(Player player, String providerId, String currencyId) {
        EconomyProvider provider = select(providerId, currencyId);
        return provider == null ? 0D : provider.getBalance(player, currencyId);
    }

    public ActionResult add(Player player, String providerId, String currencyId, double amount) {
        Supported supported = resolveSupported(providerId, currencyId);
        if (!supported.result().success()) {
            return supported.result();
        }
        return supported.provider().add(player, currencyId, amount);
    }

    public ActionResult remove(Player player, String providerId, String currencyId, double amount) {
        Supported supported = resolveSupported(providerId, currencyId);
        if (!supported.result().success()) {
            return supported.result();
        }
        return supported.provider().remove(player, currencyId, amount);
    }

    public ActionResult set(Player player, String providerId, String currencyId, double amount) {
        Supported supported = resolveSupported(providerId, currencyId);
        if (!supported.result().success()) {
            return supported.result();
        }
        return supported.provider().set(player, currencyId, amount);
    }

    private record Supported(EconomyProvider provider, ActionResult result) {
    }
}
