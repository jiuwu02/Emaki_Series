package emaki.jiuwu.craft.item.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Entry point for obtaining the {@link EmakiItemApi} implemented by the enabled
 * EmakiItem plugin instance.
 *
 * <p>The service only exists after EmakiItem has enabled, so resolve it lazily
 * rather than during your own plugin's load phase. Successful lookups are
 * cached while the owning plugin remains enabled.
 */
public final class EmakiItemApiProvider {

    private static final String PLUGIN_NAME = "EmakiItem";
    private static volatile EmakiItemApi cached;

    private EmakiItemApiProvider() {
    }

    /**
     * {@return the enabled {@link EmakiItemApi}, or an empty optional} Empty when
     * EmakiItem is absent, disabled or not exposing the API.
     */
    public static Optional<EmakiItemApi> get() {
        EmakiItemApi api = cached;
        if (api instanceof Plugin plugin && plugin.isEnabled()) {
            return Optional.of(api);
        }
        cached = null;

        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled() || !EmakiItemApi.class.isInstance(plugin)) {
            return Optional.empty();
        }
        api = EmakiItemApi.class.cast(plugin);
        cached = api;
        return Optional.of(api);
    }
}
