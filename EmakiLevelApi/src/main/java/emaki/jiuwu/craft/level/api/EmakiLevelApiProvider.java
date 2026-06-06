package emaki.jiuwu.craft.level.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class EmakiLevelApiProvider {

    private static final String PLUGIN_NAME = "EmakiLevel";
    private static volatile EmakiLevelApi cached;

    private EmakiLevelApiProvider() {
    }

    public static Optional<EmakiLevelApi> get() {
        EmakiLevelApi api = cached;
        if (api instanceof Plugin plugin && plugin.isEnabled()) {
            return Optional.of(api);
        }
        cached = null;

        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled() || !EmakiLevelApi.class.isInstance(plugin)) {
            return Optional.empty();
        }
        api = EmakiLevelApi.class.cast(plugin);
        cached = api;
        return Optional.of(api);
    }
}
