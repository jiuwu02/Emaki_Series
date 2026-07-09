package emaki.jiuwu.craft.forge.action;

import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;

public final class ForgeActionRegistrar {

    private final EmakiForgePlugin plugin;

    public ForgeActionRegistrar(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    public void register(ActionRegistry registry) {
        if (registry == null || plugin == null) {
            return;
        }
        register(registry, ForgeRefreshAction.Operation.HELD_ITEM, "emakiforge_refresh_held");
        register(registry, ForgeRefreshAction.Operation.PLAYER_INVENTORY, "emakiforge_refresh_player");
        register(registry, ForgeRefreshAction.Operation.ONLINE_PLAYERS, "emakiforge_refresh_all");
    }

    private void register(ActionRegistry registry, ForgeRefreshAction.Operation operation, String id) {
        registry.register(plugin, "emakiforge", new ForgeRefreshAction(plugin, id, operation));
    }
}
