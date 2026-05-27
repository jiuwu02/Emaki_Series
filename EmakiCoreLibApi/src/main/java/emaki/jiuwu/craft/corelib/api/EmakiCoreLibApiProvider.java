package emaki.jiuwu.craft.corelib.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EmakiCoreLibApiProvider {

    private EmakiCoreLibApiProvider() {
    }

    public static Optional<EmakiCoreLibApi> get() {
        RegisteredServiceProvider<EmakiCoreLibApi> provider = Bukkit.getServicesManager().getRegistration(EmakiCoreLibApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
