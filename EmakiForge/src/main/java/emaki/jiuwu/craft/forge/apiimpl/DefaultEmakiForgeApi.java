package emaki.jiuwu.craft.forge.apiimpl;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.api.EmakiForgeApi;
import emaki.jiuwu.craft.forge.api.ForgeCatalog;
import emaki.jiuwu.craft.forge.api.ForgeExtensions;
import emaki.jiuwu.craft.forge.api.ForgeOperations;

public final class DefaultEmakiForgeApi implements EmakiForgeApi.Bridge {

    private final EmakiForgePlugin plugin;
    private final ForgeCatalog catalog;
    private final ForgeOperations operations;
    private final ForgeExtensions extensions;

    public DefaultEmakiForgeApi(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultForgeCatalog(plugin);
        this.operations = new DefaultForgeOperations(plugin);
        this.extensions = new DefaultForgeExtensions();
    }

    @Override
    public @NotNull ApiStatus status() {
        if (!plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String pluginName = plugin.getName();
        String version = plugin.getPluginMeta().getVersion();
        return plugin.isRuntimeReady()
                ? ApiStatus.ready(pluginName, version, version)
                : ApiStatus.loading(pluginName, version, version);
    }

    @Override
    public @NotNull ForgeCatalog catalog() {
        return catalog;
    }

    @Override
    public @NotNull ForgeOperations operations() {
        return operations;
    }

    @Override
    public @NotNull ForgeExtensions extensions() {
        return extensions;
    }
}
