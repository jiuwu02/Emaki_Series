package emaki.jiuwu.craft.cooking.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Entry point for obtaining the {@link EmakiCookingApi} implemented by the
 * enabled EmakiCooking plugin instance.
 *
 * <p>The service only exists after EmakiCooking has enabled, so resolve it
 * lazily rather than during your own plugin's load phase. Successful lookups
 * are cached while the owning plugin remains enabled.
 */
public final class EmakiCookingApiProvider {

    private static final String PLUGIN_NAME = "EmakiCooking";
    private static volatile EmakiCookingApi cached;

    private EmakiCookingApiProvider() {
    }

    /**
     * {@return the enabled {@link EmakiCookingApi}, or an empty optional} Empty
     * when EmakiCooking is absent, disabled or not exposing the API.
     */
    public static Optional<EmakiCookingApi> get() {
        EmakiCookingApi api = cached;
        if (api instanceof Plugin plugin && plugin.isEnabled()) {
            return Optional.of(api);
        }
        cached = null;

        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled() || !EmakiCookingApi.class.isInstance(plugin)) {
            return Optional.empty();
        }
        api = EmakiCookingApi.class.cast(plugin);
        cached = api;
        return Optional.of(api);
    }
}
