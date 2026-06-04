package emaki.jiuwu.craft.level.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EmakiLevelApiProvider {

    private EmakiLevelApiProvider() {
    }

    public static Optional<EmakiLevelApi> get() {
        RegisteredServiceProvider<EmakiLevelApi> provider = Bukkit.getServicesManager().getRegistration(EmakiLevelApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
