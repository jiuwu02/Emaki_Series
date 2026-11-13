package emaki.jiuwu.craft.kits.apiimpl;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.kits.EmakiKitsPlugin;
import emaki.jiuwu.craft.kits.api.EmakiKitsApi;
import emaki.jiuwu.craft.kits.api.KitCatalog;
import emaki.jiuwu.craft.kits.api.KitExtensions;
import emaki.jiuwu.craft.kits.api.KitOperations;
import org.jetbrains.annotations.NotNull;

public final class DefaultEmakiKitsApi implements EmakiKitsApi.Bridge {

    private final EmakiKitsPlugin plugin;
    private final KitCatalog catalog;
    private final KitOperations operations;
    private final KitExtensions extensions;

    public DefaultEmakiKitsApi(EmakiKitsPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultKitsCatalog(plugin);
        this.operations = new DefaultKitsOperations(plugin);
        this.extensions = new DefaultKitsExtensions();
    }

    @Override
    public @NotNull ApiStatus status() {
        if (!plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String name = plugin.getName();
        String version = plugin.getPluginMeta().getVersion();
        return plugin.runtimeReady()
                ? ApiStatus.ready(name, version, version)
                : ApiStatus.loading(name, version, version);
    }

    @Override
    public @NotNull KitCatalog catalog() {
        return catalog;
    }

    @Override
    public @NotNull KitOperations operations() {
        return operations;
    }

    @Override
    public @NotNull KitExtensions extensions() {
        return extensions;
    }
}
