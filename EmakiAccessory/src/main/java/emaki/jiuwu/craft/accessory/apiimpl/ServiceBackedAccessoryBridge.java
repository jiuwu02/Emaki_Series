package emaki.jiuwu.craft.accessory.apiimpl;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.accessory.EmakiAccessoryPlugin;
import emaki.jiuwu.craft.accessory.api.AccessoryCatalog;
import emaki.jiuwu.craft.accessory.api.EmakiAccessoryApi;
import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;

public final class ServiceBackedAccessoryBridge implements EmakiAccessoryApi.Bridge {

    private static final String API_VERSION = "1.0.0";

    private final EmakiAccessoryPlugin plugin;
    private final AccessoryCatalog catalog;

    public ServiceBackedAccessoryBridge(EmakiAccessoryPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultAccessoryCatalog(plugin);
    }

    @Override
    public @NotNull ApiStatus status() {
        if (plugin == null || !plugin.isEnabled() || plugin.isShutdownStarted()) {
            return ApiStatus.notInstalled();
        }

        return plugin.contentReady()
                ? ApiStatus.ready(plugin.getName(), plugin.getPluginMeta().getVersion(), API_VERSION)
                : ApiStatus.loading(plugin.getName(), plugin.getPluginMeta().getVersion(), API_VERSION);
    }

    @Override
    public @NotNull AccessoryCatalog catalog() {
        return catalog;
    }
}
