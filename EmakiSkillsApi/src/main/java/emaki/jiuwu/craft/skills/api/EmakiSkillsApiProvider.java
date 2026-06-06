package emaki.jiuwu.craft.skills.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Entry point for obtaining the {@link EmakiSkillsApi} implemented by the
 * enabled EmakiSkills plugin instance.
 *
 * <p>The service only exists after EmakiSkills has enabled, so resolve it
 * lazily rather than during your own plugin's load phase. Successful lookups
 * are cached while the owning plugin remains enabled.
 */
public final class EmakiSkillsApiProvider {

    private static final String PLUGIN_NAME = "EmakiSkills";
    private static volatile EmakiSkillsApi cached;

    private EmakiSkillsApiProvider() {
    }

    /**
     * {@return the enabled {@link EmakiSkillsApi}, or an empty optional} Empty
     * when EmakiSkills is absent, disabled or not exposing the API.
     */
    public static Optional<EmakiSkillsApi> get() {
        EmakiSkillsApi api = cached;
        if (api instanceof Plugin plugin && plugin.isEnabled()) {
            return Optional.of(api);
        }
        cached = null;

        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled() || !EmakiSkillsApi.class.isInstance(plugin)) {
            return Optional.empty();
        }
        api = EmakiSkillsApi.class.cast(plugin);
        cached = api;
        return Optional.of(api);
    }
}
