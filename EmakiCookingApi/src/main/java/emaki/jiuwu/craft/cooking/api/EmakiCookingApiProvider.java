package emaki.jiuwu.craft.cooking.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EmakiCookingApiProvider {

    private EmakiCookingApiProvider() {
    }

    public static Optional<EmakiCookingApi> get() {
        RegisteredServiceProvider<EmakiCookingApi> provider = Bukkit.getServicesManager().getRegistration(EmakiCookingApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
