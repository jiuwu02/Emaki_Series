package emaki.jiuwu.craft.cooking.apiimpl;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.api.CookingCatalog;
import emaki.jiuwu.craft.cooking.api.CookingNutrition;
import emaki.jiuwu.craft.cooking.api.CookingOperations;
import emaki.jiuwu.craft.cooking.api.EmakiCookingApi;

public final class DefaultEmakiCookingApi implements EmakiCookingApi.Bridge {

    private final EmakiCookingPlugin plugin;
    private final CookingNutrition nutrition;
    private final CookingCatalog catalog;
    private final CookingOperations operations;

    public DefaultEmakiCookingApi(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
        this.nutrition = new DefaultCookingNutrition(plugin);
        this.catalog = new DefaultCookingCatalog(plugin);
        this.operations = new DefaultCookingOperations(plugin);
    }

    @Override
    public @NotNull ApiStatus status() {
        if (!plugin.isEnabled()) {
            return ApiStatus.notInstalled();
        }
        String pluginName = plugin.getName();
        String version = plugin.getPluginMeta().getVersion();
        boolean ready = plugin.publicApiReady()
                && plugin.recipeService() != null
                && plugin.rewardService() != null
                && plugin.stationTracker() != null
                && plugin.nutritionService() != null
                && plugin.nutritionTypeRegistry() != null
                && plugin.choppingBoardRecipeLoader() != null
                && plugin.wokRecipeLoader() != null
                && plugin.grinderRecipeLoader() != null
                && plugin.steamerRecipeLoader() != null
                && plugin.ovenRecipeLoader() != null
                && plugin.juicerRecipeLoader() != null
                && plugin.fermentationBarrelRecipeLoader() != null
                && plugin.choppingBoardRuntimeService() != null
                && plugin.wokRuntimeService() != null
                && plugin.grinderRuntimeService() != null
                && plugin.steamerRuntimeService() != null
                && plugin.ovenRuntimeService() != null
                && plugin.juicerRuntimeService() != null
                && plugin.fermentationBarrelRuntimeService() != null;
        return ready
                ? ApiStatus.ready(pluginName, version, version)
                : ApiStatus.loading(pluginName, version, version);
    }

    @Override
    public @NotNull CookingNutrition nutrition() {
        return nutrition;
    }

    @Override
    public @NotNull CookingCatalog catalog() {
        return catalog;
    }

    @Override
    public @NotNull CookingOperations operations() {
        return operations;
    }
}
