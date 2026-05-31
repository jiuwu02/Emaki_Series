package emaki.jiuwu.craft.strengthen.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Entry point for obtaining the {@link EmakiStrengthenApi} that EmakiStrengthen
 * registers with the Bukkit {@code ServicesManager}.
 *
 * <p>The service only exists after EmakiStrengthen has enabled, so resolve it
 * lazily rather than caching it during your own plugin's load phase.
 */
public final class EmakiStrengthenApiProvider {

    private EmakiStrengthenApiProvider() {
    }

    /**
     * {@return the registered {@link EmakiStrengthenApi}, or an empty optional}
     * Empty when EmakiStrengthen is absent or has not finished enabling.
     */
    public static Optional<EmakiStrengthenApi> get() {
        RegisteredServiceProvider<EmakiStrengthenApi> provider = Bukkit.getServicesManager().getRegistration(EmakiStrengthenApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
