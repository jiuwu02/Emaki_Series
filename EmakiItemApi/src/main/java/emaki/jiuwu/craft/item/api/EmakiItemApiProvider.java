package emaki.jiuwu.craft.item.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EmakiItemApiProvider {

    private EmakiItemApiProvider() {
    }

    public static Optional<EmakiItemApi> get() {
        RegisteredServiceProvider<EmakiItemApi> provider = Bukkit.getServicesManager().getRegistration(EmakiItemApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
