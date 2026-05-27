package emaki.jiuwu.craft.gem.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EmakiGemApiProvider {

    private EmakiGemApiProvider() {
    }

    public static Optional<EmakiGemApi> get() {
        RegisteredServiceProvider<EmakiGemApi> provider = Bukkit.getServicesManager().getRegistration(EmakiGemApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
