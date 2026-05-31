package emaki.jiuwu.craft.corelib.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Entry point for obtaining the {@link EmakiCoreLibApi} that EmakiCoreLib registers with the
 * Bukkit {@code ServicesManager}.
 *
 * <p>The service only exists after EmakiCoreLib has enabled, so resolve it lazily
 * rather than caching it during your own plugin's load phase.
 */
public final class EmakiCoreLibApiProvider {

    private EmakiCoreLibApiProvider() {
    }

    /**
     * {@return the registered {@link EmakiCoreLibApi}, or an empty optional} Empty when
     * EmakiCoreLib is absent or has not finished enabling.
     */
    public static Optional<EmakiCoreLibApi> get() {
        RegisteredServiceProvider<EmakiCoreLibApi> provider = Bukkit.getServicesManager().getRegistration(EmakiCoreLibApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
