package emaki.jiuwu.craft.strengthen.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EmakiStrengthenApiProvider {

    private EmakiStrengthenApiProvider() {
    }

    public static Optional<EmakiStrengthenApi> get() {
        RegisteredServiceProvider<EmakiStrengthenApi> provider = Bukkit.getServicesManager().getRegistration(EmakiStrengthenApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
