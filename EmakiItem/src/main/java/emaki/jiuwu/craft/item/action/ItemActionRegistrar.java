package emaki.jiuwu.craft.item.action;

import java.util.List;

import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemActionRegistrar {

    private final EmakiItemPlugin plugin;

    public ItemActionRegistrar(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(ActionRegistry registry) {
        if (registry == null) {
            return;
        }
        for (String id : List.of("emakiitem_update", "emakiitemupdate")) {
            registry.register(plugin, "emakiitem", new ItemHeldItemAction(plugin, id, ItemHeldItemAction.Operation.UPDATE));
        }
        for (String id : List.of("emakiitem_rerender", "emakiitemrerender")) {
            registry.register(plugin, "emakiitem", new ItemHeldItemAction(plugin, id, ItemHeldItemAction.Operation.RERENDER));
        }
        for (String id : List.of("emakiitem_repair_amount", "emakiitemrepairamount")) {
            registry.register(plugin, "emakiitem", new ItemHeldItemAction(plugin, id, ItemHeldItemAction.Operation.REPAIR_AMOUNT));
        }
        for (String id : List.of("emakiitem_damage", "emakiitemdamage")) {
            registry.register(plugin, "emakiitem", new ItemHeldItemAction(plugin, id, ItemHeldItemAction.Operation.DAMAGE));
        }
        for (String id : List.of("emakiitem_set_damage", "emakiitemsetdamage")) {
            registry.register(plugin, "emakiitem", new ItemHeldItemAction(plugin, id, ItemHeldItemAction.Operation.SET_DAMAGE));
        }
        for (String id : List.of("emakiitem_set_durability", "emakiitemsetdurability")) {
            registry.register(plugin, "emakiitem", new ItemHeldItemAction(plugin, id, ItemHeldItemAction.Operation.SET_DURABILITY));
        }
    }
}
