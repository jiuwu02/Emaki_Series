package emaki.jiuwu.craft.strengthen;

import emaki.jiuwu.craft.corelib.item.AbstractPlayerItemRefreshListener;
import emaki.jiuwu.craft.corelib.item.PlayerItemRefreshService;

final class StrengthenItemRefreshListener extends AbstractPlayerItemRefreshListener {

    private final EmakiStrengthenPlugin plugin;

    StrengthenItemRefreshListener(EmakiStrengthenPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @Override
    protected PlayerItemRefreshService refreshService() {
        return plugin.refreshService();
    }
}
