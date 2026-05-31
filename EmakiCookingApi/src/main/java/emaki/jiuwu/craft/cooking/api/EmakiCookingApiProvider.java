package emaki.jiuwu.craft.cooking.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Entry point for obtaining the {@link EmakiCookingApi} that EmakiCooking registers with the
 * Bukkit {@code ServicesManager}.
 *
 * <p>The service only exists after EmakiCooking has enabled, so resolve it lazily
 * rather than caching it during your own plugin's load phase.
 */
public final class EmakiCookingApiProvider {

    private EmakiCookingApiProvider() {
    }

    /**
     * {@return the registered {@link EmakiCookingApi}, or an empty optional} Empty when
     * EmakiCooking is absent or has not finished enabling.
     */
    public static Optional<EmakiCookingApi> get() {
        RegisteredServiceProvider<EmakiCookingApi> provider = Bukkit.getServicesManager().getRegistration(EmakiCookingApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
