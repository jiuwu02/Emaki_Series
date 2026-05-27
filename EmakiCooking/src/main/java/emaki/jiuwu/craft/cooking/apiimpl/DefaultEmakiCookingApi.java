package emaki.jiuwu.craft.cooking.apiimpl;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.api.EmakiCookingApi;

public final class DefaultEmakiCookingApi implements EmakiCookingApi {

    private final EmakiCookingPlugin plugin;

    public DefaultEmakiCookingApi(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String apiVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String pluginName() {
        return plugin.getName();
    }

    @Override
    public boolean isReady() {
        return plugin.isEnabled() && plugin.recipeService() != null;
    }
}
