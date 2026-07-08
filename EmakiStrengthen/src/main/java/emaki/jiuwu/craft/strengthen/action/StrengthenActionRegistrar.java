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
        register(registry, StrengthenHeldItemAction.Operation.RERENDER, List.of("emakistrengthen_rerender", "emakistrengthenrerender", "strengthen_rerender", "strengthenrerender"));
        register(registry, StrengthenHeldItemAction.Operation.SET_STAR, List.of("emakistrengthen_set_star", "emakistrengthensetstar", "strengthen_set_star", "strengthensetstar"));
        register(registry, StrengthenHeldItemAction.Operation.ADD_STAR, List.of("emakistrengthen_add_star", "emakistrengthenaddstar", "strengthen_add_star", "strengthenaddstar"));
        register(registry, StrengthenHeldItemAction.Operation.REMOVE_STAR, List.of("emakistrengthen_remove_star", "emakistrengthenremovestar", "strengthen_remove_star", "strengthenremovestar"));
        register(registry, StrengthenHeldItemAction.Operation.RESET_STAR, List.of("emakistrengthen_reset_star", "emakistrengthenresetstar", "strengthen_reset_star", "strengthenresetstar"));
        register(registry, StrengthenHeldItemAction.Operation.CLEAR_LAYER, List.of("emakistrengthen_remove_layer", "emakistrengthenremovelayer", "emakistrengthen_clear_layer", "emakistrengthenclearlayer", "strengthen_remove_layer", "strengthenremovelayer", "strengthen_clear_layer", "strengthenclearlayer"));
    }

    private void register(ActionRegistry registry, StrengthenHeldItemAction.Operation operation, List<String> ids) {
        for (String id : ids) {
            registry.register(plugin, "emakistrengthen", new StrengthenHeldItemAction(plugin, id, operation));
        }
    }
}
