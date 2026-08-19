package emaki.jiuwu.craft.item.apiimpl;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.item.api.ItemCatalog;
import emaki.jiuwu.craft.item.api.ItemExtensions;
import emaki.jiuwu.craft.item.api.ItemMigration;
import emaki.jiuwu.craft.item.api.ItemOperations;
import emaki.jiuwu.craft.item.api.ItemRepair;
import emaki.jiuwu.craft.item.api.ItemState;
import emaki.jiuwu.craft.item.service.EmakiItemStateService;

public final class DefaultEmakiItemApi implements EmakiItemApi.Bridge {

    private final EmakiItemPlugin plugin;
    private final ItemCatalog catalog;
    private final ItemOperations operations;
    private final ItemRepair repair;
    private final ItemMigration migration;
    private final ItemExtensions extensions;
    private final ItemState state;

    public DefaultEmakiItemApi(EmakiItemPlugin plugin) {
        this.plugin = plugin;
        this.catalog = new DefaultItemCatalog(plugin);
        this.operations = new DefaultItemOperations(plugin);
        this.repair = new DefaultItemRepair(plugin);
        this.migration = new DefaultItemMigration(plugin);
        this.extensions = new DefaultItemExtensions(plugin);
        this.state = new EmakiItemStateService();
    }

    @Override
    public @NotNull ApiStatus status() {
        if (!plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String pluginName = plugin.getName();
        String version = plugin.getPluginMeta().getVersion();
        return plugin.runtimeReady()
                ? ApiStatus.ready(pluginName, version, version)
                : ApiStatus.loading(pluginName, version, version);
    }

    @Override
    public @NotNull ItemCatalog catalog() {
        return catalog;
    }

    @Override
    public @NotNull ItemOperations operations() {
        return operations;
    }

    @Override
    public @NotNull ItemRepair repair() {
        return repair;
    }

    @Override
    public @NotNull ItemMigration migration() {
        return migration;
    }

    @Override
    public @NotNull ItemExtensions extensions() {
        return extensions;
    }

    @Override
    public @NotNull ItemState state() {
        return plugin.stateService() == null ? state : plugin.stateService();
    }
}
