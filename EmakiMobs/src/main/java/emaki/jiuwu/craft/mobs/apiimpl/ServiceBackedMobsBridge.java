package emaki.jiuwu.craft.mobs.apiimpl;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;
import emaki.jiuwu.craft.mobs.api.EmakiMobsApi;
import emaki.jiuwu.craft.mobs.api.MobCatalog;
import org.jetbrains.annotations.NotNull;

public final class ServiceBackedMobsBridge implements EmakiMobsApi.Bridge {

    private static final String API_VERSION = "1.0.0";

    private final EmakiMobsPlugin plugin;
    private final MobCatalog catalog;

    public ServiceBackedMobsBridge(EmakiMobsPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultMobCatalog(plugin);
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
    public @NotNull MobCatalog catalog() {
        return catalog;
    }
}
