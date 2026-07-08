package emaki.jiuwu.craft.gem.action;

import java.util.List;

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
        register(registry, GemHeldItemAction.Operation.OPEN_SOCKET, List.of("emakigem_open_socket", "emakigemopensocket"));
        register(registry, GemHeldItemAction.Operation.INLAY, List.of("emakigem_inlay", "emakigeminlay"));
        register(registry, GemHeldItemAction.Operation.EXTRACT, List.of("emakigem_extract", "emakigemextract"));
        register(registry, GemHeldItemAction.Operation.UPGRADE_GEM_ITEM, List.of("emakigem_upgrade_gem_item", "emakigemupgradegemitem"));
        register(registry, GemHeldItemAction.Operation.CLEAR_LAYER, List.of("emakigem_clear_layer", "emakigemclearlayer"));
    }

    private void register(ActionRegistry registry, GemHeldItemAction.Operation operation, List<String> ids) {
        for (String id : ids) {
            registry.register(plugin, "emakigem", new GemHeldItemAction(plugin, id, operation));
        }
    }
}
