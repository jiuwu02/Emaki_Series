package emaki.jiuwu.craft.accessory.apiimpl;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.accessory.EmakiAccessoryPlugin;
import emaki.jiuwu.craft.accessory.api.AccessoryCatalog;
import emaki.jiuwu.craft.accessory.api.EmakiAccessoryApi;
import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;

/**
 * Connects the public API surface to the running services.
 *
 * <p>{@link #status()} reports unavailable during shutdown, so a caller that queries while the plugin is
 * tearing down gets a clean "not ready" answer instead of reading half-released state.
 */
public final class ServiceBackedAccessoryBridge implements EmakiAccessoryApi.Bridge {

    private static final String API_VERSION = "1.0.0";

    private final EmakiAccessoryPlugin plugin;
    private final AccessoryCatalog catalog;

    /**
     * Creates the bridge.
     *
     * @param plugin the owning plugin
     */
    public ServiceBackedAccessoryBridge(EmakiAccessoryPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultAccessoryCatalog(plugin);
    }

    @Override
    public @NotNull ApiStatus status() {
        if (plugin == null || !plugin.isEnabled() || plugin.isShutdownStarted()) {
            return ApiStatus.notInstalled();
        }
        // Previously an unconditional ready(): being installed and enabled says nothing about whether
        // the part registry has been loaded, so a reload window reported ready with an empty catalog.
        return plugin.contentReady()
                ? ApiStatus.ready(plugin.getName(), plugin.getPluginMeta().getVersion(), API_VERSION)
                : ApiStatus.loading(plugin.getName(), plugin.getPluginMeta().getVersion(), API_VERSION);
    }

    @Override
    public @NotNull AccessoryCatalog catalog() {
        return catalog;
    }
}
