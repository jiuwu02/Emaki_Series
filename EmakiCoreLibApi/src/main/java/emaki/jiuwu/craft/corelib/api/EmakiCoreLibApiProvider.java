package emaki.jiuwu.craft.corelib.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Entry point for obtaining the {@link EmakiCoreLibApi} implemented by the
 * enabled EmakiCoreLib plugin instance.
 *
 * <p>The service only exists after EmakiCoreLib has enabled, so resolve it
 * lazily rather than during your own plugin's load phase. Successful lookups
 * are cached while the owning plugin remains enabled.
 */
public final class EmakiCoreLibApiProvider {

    private static final String PLUGIN_NAME = "EmakiCoreLib";
    private static volatile EmakiCoreLibApi cached;

    private EmakiCoreLibApiProvider() {
    }

    /**
     * {@return the enabled {@link EmakiCoreLibApi}, or an empty optional} Empty
     * when EmakiCoreLib is absent, disabled or not exposing the API.
     */
    public static Optional<EmakiCoreLibApi> get() {
        EmakiCoreLibApi api = cached;
        if (api instanceof Plugin plugin && plugin.isEnabled()) {
            return Optional.of(api);
        }
        cached = null;

        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled() || !EmakiCoreLibApi.class.isInstance(plugin)) {
            return Optional.empty();
        }
        api = EmakiCoreLibApi.class.cast(plugin);
        cached = api;
        return Optional.of(api);
    }
}
