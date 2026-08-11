package emaki.jiuwu.craft.level.apiimpl;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.api.ExpSourceProvider;
import emaki.jiuwu.craft.level.api.ExpSourceRegistration;
import emaki.jiuwu.craft.level.api.LevelExtensions;

/** Default experience source registration adapter. */
public final class DefaultLevelExtensions implements LevelExtensions {

    private final EmakiLevelPlugin plugin;

    public DefaultLevelExtensions(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ExpSourceRegistration registerExpSource(Plugin owner, ExpSourceProvider provider) {
        return plugin == null || plugin.expSourceRegistry() == null
                ? ExpSourceRegistration.noop()
                : plugin.expSourceRegistry().register(owner, provider);
    }
}
