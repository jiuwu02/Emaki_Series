package emaki.jiuwu.craft.structure.apiimpl;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.api.EmakiStructureApi;
import emaki.jiuwu.craft.structure.api.StructureCatalog;
import org.jetbrains.annotations.NotNull;

public final class ServiceBackedStructureBridge implements EmakiStructureApi.Bridge {

    private static final String API_VERSION = "1.0.0";

    private final EmakiStructurePlugin plugin;
    private final StructureCatalog catalog;

    public ServiceBackedStructureBridge(EmakiStructurePlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultStructureCatalog(plugin);
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
    public @NotNull StructureCatalog catalog() {
        return catalog;
    }
}
