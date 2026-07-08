package emaki.jiuwu.craft.forge.action;

import java.util.List;

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
        register(registry, ForgeRefreshAction.Operation.HELD_ITEM, List.of("emakiforge_refresh_held", "emakiforgerefreshheld"));
        register(registry, ForgeRefreshAction.Operation.PLAYER_INVENTORY, List.of("emakiforge_refresh_player", "emakiforgerefreshplayer"));
        register(registry, ForgeRefreshAction.Operation.ONLINE_PLAYERS, List.of("emakiforge_refresh_all", "emakiforgerefreshall"));
    }

    private void register(ActionRegistry registry, ForgeRefreshAction.Operation operation, List<String> ids) {
        for (String id : ids) {
            registry.register(plugin, "emakiforge", new ForgeRefreshAction(plugin, id, operation));
        }
    }
}
