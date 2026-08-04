package emaki.jiuwu.craft.level.apiimpl;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.api.EmakiLevelApi;
import emaki.jiuwu.craft.level.api.LevelCatalog;
import emaki.jiuwu.craft.level.api.LevelExtensions;
import emaki.jiuwu.craft.level.api.LevelOperations;

/** Named runtime bridge composed from the three public capability implementations. */
public final class DefaultEmakiLevelApi implements EmakiLevelApi.Bridge {

    private final EmakiLevelPlugin plugin;
    private final LevelCatalog catalog;
    private final LevelOperations operations;
    private final LevelExtensions extensions;

    public DefaultEmakiLevelApi(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultLevelCatalog(plugin);
        this.operations = new DefaultLevelOperations(plugin);
        this.extensions = new DefaultLevelExtensions(plugin);
    }

    @Override
    public @NotNull ApiStatus status() {
        if (plugin == null || !plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String version = plugin.getPluginMeta().getVersion();
        return plugin.typeRegistry() != null && plugin.dataStore() != null && plugin.levelService() != null
                ? ApiStatus.ready(plugin.getName(), version, version)
                : ApiStatus.loading(plugin.getName(), version, version);
    }

    @Override
    public @NotNull LevelCatalog catalog() {
        return catalog;
    }

    @Override
    public @NotNull LevelOperations operations() {
        return operations;
    }

    @Override
    public @NotNull LevelExtensions extensions() {
        return extensions;
    }
}
