package emaki.jiuwu.craft.strengthen.action;

import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class StrengthenActionRegistrar {

    private final EmakiStrengthenPlugin plugin;

    public StrengthenActionRegistrar(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(ActionRegistry registry) {
        if (registry == null) {
            return;
        }
        register(registry, StrengthenHeldItemAction.Operation.RERENDER, "emakistrengthen_rerender");
        register(registry, StrengthenHeldItemAction.Operation.SET_STAR, "emakistrengthen_set_star");
        register(registry, StrengthenHeldItemAction.Operation.ADD_STAR, "emakistrengthen_add_star");
        register(registry, StrengthenHeldItemAction.Operation.REMOVE_STAR, "emakistrengthen_remove_star");
        register(registry, StrengthenHeldItemAction.Operation.RESET_STAR, "emakistrengthen_reset_star");
        register(registry, StrengthenHeldItemAction.Operation.CLEAR_LAYER, "emakistrengthen_clear_layer");
    }

    private void register(ActionRegistry registry, StrengthenHeldItemAction.Operation operation, String id) {
        registry.register(plugin, "emakistrengthen", new StrengthenHeldItemAction(plugin, id, operation));
    }
}
