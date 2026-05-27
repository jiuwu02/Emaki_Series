package emaki.jiuwu.craft.gem.apiimpl;

import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.EmakiGemApi;

public final class DefaultEmakiGemApi implements EmakiGemApi {

    private final EmakiGemPlugin plugin;

    public DefaultEmakiGemApi(EmakiGemPlugin plugin) {
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
        return plugin.isEnabled() && plugin.stateService() != null;
    }
}
