package emaki.jiuwu.craft.gem.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Entry point for obtaining the {@link EmakiGemApi} that EmakiGem registers with the
 * Bukkit {@code ServicesManager}.
 *
 * <p>The service only exists after EmakiGem has enabled, so resolve it lazily
 * rather than caching it during your own plugin's load phase.
 */
public final class EmakiGemApiProvider {

    private EmakiGemApiProvider() {
    }

    /**
     * {@return the registered {@link EmakiGemApi}, or an empty optional} Empty when
     * EmakiGem is absent or has not finished enabling.
     */
    public static Optional<EmakiGemApi> get() {
        RegisteredServiceProvider<EmakiGemApi> provider = Bukkit.getServicesManager().getRegistration(EmakiGemApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
