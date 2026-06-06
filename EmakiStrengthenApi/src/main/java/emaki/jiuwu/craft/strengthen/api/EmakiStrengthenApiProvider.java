package emaki.jiuwu.craft.strengthen.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Entry point for obtaining the {@link EmakiStrengthenApi} implemented by the
 * enabled EmakiStrengthen plugin instance.
 *
 * <p>The service only exists after EmakiStrengthen has enabled, so resolve it
 * lazily rather than during your own plugin's load phase. Successful lookups
 * are cached while the owning plugin remains enabled.
 */
public final class EmakiStrengthenApiProvider {

    private static final String PLUGIN_NAME = "EmakiStrengthen";
    private static volatile EmakiStrengthenApi cached;

    private EmakiStrengthenApiProvider() {
    }

    /**
     * {@return the enabled {@link EmakiStrengthenApi}, or an empty optional}
     * Empty when EmakiStrengthen is absent, disabled or not exposing the API.
     */
    public static Optional<EmakiStrengthenApi> get() {
        EmakiStrengthenApi api = cached;
        if (api instanceof Plugin plugin && plugin.isEnabled()) {
            return Optional.of(api);
        }
        cached = null;

        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled() || !EmakiStrengthenApi.class.isInstance(plugin)) {
            return Optional.empty();
        }
        api = EmakiStrengthenApi.class.cast(plugin);
        cached = api;
        return Optional.of(api);
    }
}
