package emaki.jiuwu.craft.forge;

import emaki.jiuwu.craft.corelib.item.AbstractPlayerItemRefreshListener;
import emaki.jiuwu.craft.corelib.item.PlayerItemRefreshService;

final class ForgeItemRefreshListener extends AbstractPlayerItemRefreshListener {

    private final EmakiForgePlugin plugin;

    ForgeItemRefreshListener(EmakiForgePlugin plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @Override
    protected PlayerItemRefreshService refreshService() {
        return plugin.itemRefreshService();
    }
}
