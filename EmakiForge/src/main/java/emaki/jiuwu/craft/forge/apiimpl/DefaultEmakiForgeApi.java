package emaki.jiuwu.craft.forge.apiimpl;

import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.api.EmakiForgeApi;

public final class DefaultEmakiForgeApi implements EmakiForgeApi.Bridge {

    private final EmakiForgePlugin plugin;

    public DefaultEmakiForgeApi(EmakiForgePlugin plugin) {
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
        return plugin.isEnabled() && plugin.isRuntimeReady();
    }
}
