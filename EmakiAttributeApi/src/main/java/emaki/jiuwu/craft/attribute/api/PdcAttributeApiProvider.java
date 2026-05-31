package emaki.jiuwu.craft.attribute.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Entry point for obtaining the {@link PdcAttributeApi} implementation that
 * EmakiAttribute registers with the Bukkit {@code ServicesManager}.
 *
 * <p>Typical usage from a third-party plugin:
 * <pre>{@code
 * PdcAttributeApiProvider.get().ifPresent(api -> api.registerSource("myplugin"));
 * }</pre>
 *
 * <p>The service is only present once EmakiAttribute has enabled, so callers
 * should resolve it lazily rather than caching during their own load phase.
 */
public final class PdcAttributeApiProvider {

    private PdcAttributeApiProvider() {
    }

    /**
     * {@return the registered {@link PdcAttributeApi}, or an empty optional}
     * Empty when EmakiAttribute is absent or has not finished enabling.
     */
    public static Optional<PdcAttributeApi> get() {
        RegisteredServiceProvider<PdcAttributeApi> provider = Bukkit.getServicesManager().getRegistration(PdcAttributeApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}
