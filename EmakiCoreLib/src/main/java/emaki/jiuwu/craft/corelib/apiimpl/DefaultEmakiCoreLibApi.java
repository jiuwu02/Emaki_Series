package emaki.jiuwu.craft.corelib.apiimpl;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;

public final class DefaultEmakiCoreLibApi implements EmakiCoreLibApi.Bridge {

    private final EmakiCoreLibPlugin plugin;

    public DefaultEmakiCoreLibApi(EmakiCoreLibPlugin plugin) {
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
        return plugin.isEnabled() && plugin.messageService() != null;
    }
}
