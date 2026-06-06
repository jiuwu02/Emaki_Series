package emaki.jiuwu.craft.gem.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Entry point for obtaining the {@link EmakiGemApi} implemented by the enabled
 * EmakiGem plugin instance.
 *
 * <p>The service only exists after EmakiGem has enabled, so resolve it lazily
 * rather than during your own plugin's load phase. Successful lookups are
 * cached while the owning plugin remains enabled.
 */
public final class EmakiGemApiProvider {

    private static final String PLUGIN_NAME = "EmakiGem";
    private static volatile EmakiGemApi cached;

    private EmakiGemApiProvider() {
    }

    /**
     * {@return the enabled {@link EmakiGemApi}, or an empty optional} Empty when
     * EmakiGem is absent, disabled or not exposing the API.
     */
    public static Optional<EmakiGemApi> get() {
        EmakiGemApi api = cached;
        if (api instanceof Plugin plugin && plugin.isEnabled()) {
            return Optional.of(api);
        }
        cached = null;

        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled() || !EmakiGemApi.class.isInstance(plugin)) {
            return Optional.empty();
        }
        api = EmakiGemApi.class.cast(plugin);
        cached = api;
        return Optional.of(api);
    }
}
