package emaki.jiuwu.craft.forge.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Entry point for obtaining the {@link EmakiForgeApi} implemented by the enabled
 * EmakiForge plugin instance.
 *
 * <p>The service only exists after EmakiForge has enabled, so resolve it lazily
 * rather than during your own plugin's load phase. Successful lookups are
 * cached while the owning plugin remains enabled.
 */
public final class EmakiForgeApiProvider {

    private static final String PLUGIN_NAME = "EmakiForge";
    private static volatile EmakiForgeApi cached;

    private EmakiForgeApiProvider() {
    }

    /**
     * {@return the enabled {@link EmakiForgeApi}, or an empty optional} Empty when
     * EmakiForge is absent, disabled or not exposing the API.
     */
    public static Optional<EmakiForgeApi> get() {
        EmakiForgeApi api = cached;
        if (api instanceof Plugin plugin && plugin.isEnabled()) {
            return Optional.of(api);
        }
        cached = null;

        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled() || !EmakiForgeApi.class.isInstance(plugin)) {
            return Optional.empty();
        }
        api = EmakiForgeApi.class.cast(plugin);
        cached = api;
        return Optional.of(api);
    }
}
