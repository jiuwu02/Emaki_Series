package emaki.jiuwu.craft.cooking.apiimpl;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.api.CookingCatalog;
import emaki.jiuwu.craft.cooking.api.CookingNutrition;
import emaki.jiuwu.craft.cooking.api.CookingOperations;
import emaki.jiuwu.craft.cooking.api.EmakiCookingApi;

/**
 * {@link EmakiCookingApi.Bridge} 的运行时实现。
 *
 * <p>三个层实现只构造一次并复用。{@link #status()} 的 ready 判定要求配方服务与营养注册表都在位，
 * 比旧实现只看配方服务更严；营养子系统是否被服主关闭由
 * {@link CookingNutrition#enabled()} 单独表达，不混进 status。
 */
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
        String version = plugin.getDescription().getVersion();
        boolean ready = plugin.recipeService() != null && plugin.nutritionTypeRegistry() != null;
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
