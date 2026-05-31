package emaki.jiuwu.craft.forge.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Entry point for obtaining the {@link EmakiForgeApi} that EmakiForge registers with the
 * Bukkit {@code ServicesManager}.
 *
 * <p>The service only exists after EmakiForge has enabled, so resolve it lazily
 * rather than caching it during your own plugin's load phase.
 */
public final class EmakiForgeApiProvider {

    private EmakiForgeApiProvider() {
    }

    /**
     * {@return the registered {@link EmakiForgeApi}, or an empty optional} Empty when
     * EmakiForge is absent or has not finished enabling.
     */
    public static Optional<EmakiForgeApi> get() {
        RegisteredServiceProvider<EmakiForgeApi> provider = Bukkit.getServicesManager().getRegistration(EmakiForgeApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
