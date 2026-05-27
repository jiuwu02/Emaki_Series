package emaki.jiuwu.craft.forge.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EmakiForgeApiProvider {

    private EmakiForgeApiProvider() {
    }

    public static Optional<EmakiForgeApi> get() {
        RegisteredServiceProvider<EmakiForgeApi> provider = Bukkit.getServicesManager().getRegistration(EmakiForgeApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
