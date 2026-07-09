package emaki.jiuwu.craft.gem.action;

import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;

public final class GemActionRegistrar {

    private final EmakiGemPlugin plugin;

    public GemActionRegistrar(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(ActionRegistry registry) {
        if (registry == null) {
            return;
        }
        register(registry, GemHeldItemAction.Operation.OPEN_SOCKET, "emakigem_open_socket");
        register(registry, GemHeldItemAction.Operation.INLAY, "emakigem_inlay");
        register(registry, GemHeldItemAction.Operation.EXTRACT, "emakigem_extract");
        register(registry, GemHeldItemAction.Operation.UPGRADE_GEM_ITEM, "emakigem_upgrade_gem_item");
        register(registry, GemHeldItemAction.Operation.CLEAR_LAYER, "emakigem_clear_layer");
    }

    private void register(ActionRegistry registry, GemHeldItemAction.Operation operation, String id) {
        registry.register(plugin, "emakigem", new GemHeldItemAction(plugin, id, operation));
    }
}
