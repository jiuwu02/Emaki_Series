package emaki.jiuwu.craft.strengthen.action;

import java.util.List;

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
        register(registry, StrengthenHeldItemAction.Operation.RERENDER, List.of("emakistrengthen_rerender", "emakistrengthenrerender"));
        register(registry, StrengthenHeldItemAction.Operation.SET_STAR, List.of("emakistrengthen_set_star", "emakistrengthensetstar"));
        register(registry, StrengthenHeldItemAction.Operation.ADD_STAR, List.of("emakistrengthen_add_star", "emakistrengthenaddstar"));
        register(registry, StrengthenHeldItemAction.Operation.RESET_STAR, List.of("emakistrengthen_reset_star", "emakistrengthenresetstar"));
        register(registry, StrengthenHeldItemAction.Operation.CLEAR_LAYER, List.of("emakistrengthen_remove_layer", "emakistrengthenremovelayer", "emakistrengthen_clear_layer", "emakistrengthenclearlayer"));
    }

    private void register(ActionRegistry registry, StrengthenHeldItemAction.Operation operation, List<String> ids) {
        for (String id : ids) {
            registry.register(plugin, "emakistrengthen", new StrengthenHeldItemAction(plugin, id, operation));
        }
    }
}
