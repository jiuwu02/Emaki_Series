package emaki.jiuwu.craft.strengthen.apiimpl;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi;
import emaki.jiuwu.craft.strengthen.api.ItemMastery;
import emaki.jiuwu.craft.strengthen.api.StrengthenCatalog;
import emaki.jiuwu.craft.strengthen.api.StrengthenOperations;

public final class DefaultEmakiStrengthenApi implements EmakiStrengthenApi.Bridge {

    private final EmakiStrengthenPlugin plugin;
    private final ItemMastery mastery;
    private final StrengthenCatalog catalog;
    private final StrengthenOperations operations;

    public DefaultEmakiStrengthenApi(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
        this.mastery = new DefaultItemMastery(plugin);
        this.catalog = new DefaultStrengthenCatalog(plugin);
        this.operations = new DefaultStrengthenOperations(plugin);
    }

    @Override
    public @NotNull ApiStatus status() {
        if (!plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String version = plugin.getPluginMeta().getVersion();
        boolean ready = plugin.contentReady()
                && plugin.recipeLoader() != null
                && plugin.attemptService() != null
                && plugin.transferService() != null
                && plugin.refreshService() != null
                && plugin.strengthenGuiService() != null
                && plugin.attemptService().accepting();
        return ready
                ? ApiStatus.ready(plugin.getName(), version, version)
                : ApiStatus.loading(plugin.getName(), version, version);
    }

    @Override
    public @NotNull ItemMastery mastery() {
        return mastery;
    }

    @Override
    public @NotNull StrengthenCatalog catalog() {
        return catalog;
    }

    @Override
    public @NotNull StrengthenOperations operations() {
        return operations;
    }
}
