package emaki.jiuwu.craft.attribute.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Entry point for obtaining the {@link PdcAttributeApi} implemented by the
 * enabled EmakiAttribute plugin instance.
 *
 * <p>Typical usage from a third-party plugin:
 * <pre>{@code
 * PdcAttributeApiProvider.get().ifPresent(api -> api.registerSource("myplugin"));
 * }</pre>
 *
 * <p>The service is only present once EmakiAttribute has enabled, so callers
 * should resolve it lazily rather than during their own load phase. Successful
 * lookups are cached while the owning plugin remains enabled.
 */
public final class PdcAttributeApiProvider {

    private static final String PLUGIN_NAME = "EmakiAttribute";
    private static volatile PdcAttributeApi cached;

    private PdcAttributeApiProvider() {
    }

    /**
     * {@return the enabled {@link PdcAttributeApi}, or an empty optional} Empty
     * when EmakiAttribute is absent, disabled or not exposing the API.
     */
    public static Optional<PdcAttributeApi> get() {
        PdcAttributeApi api = cached;
        if (api instanceof Plugin plugin && plugin.isEnabled()) {
            return Optional.of(api);
        }
        cached = null;

        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled() || !PdcAttributeApi.class.isInstance(plugin)) {
            return Optional.empty();
        }
        api = PdcAttributeApi.class.cast(plugin);
        cached = api;
        return Optional.of(api);
    }
}
