package emaki.jiuwu.craft.item.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Entry point for obtaining the {@link EmakiItemApi} that EmakiItem registers
 * with the Bukkit {@code ServicesManager}.
 *
 * <p>The service only exists after EmakiItem has enabled, so resolve it lazily
 * rather than caching it during your own plugin's load phase.
 */
public final class EmakiItemApiProvider {

    private EmakiItemApiProvider() {
    }

    /**
     * {@return the registered {@link EmakiItemApi}, or an empty optional} Empty
     * when EmakiItem is absent or has not finished enabling.
     */
    public static Optional<EmakiItemApi> get() {
        RegisteredServiceProvider<EmakiItemApi> provider = Bukkit.getServicesManager().getRegistration(EmakiItemApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
