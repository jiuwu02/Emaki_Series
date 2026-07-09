package emaki.jiuwu.craft.item.action;

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
        registry.register(plugin, "emakiitem", new ItemHeldItemAction(plugin, "emakiitem_update", ItemHeldItemAction.Operation.UPDATE));
        registry.register(plugin, "emakiitem", new ItemHeldItemAction(plugin, "emakiitem_rerender", ItemHeldItemAction.Operation.RERENDER));
        registry.register(plugin, "emakiitem", new ItemHeldItemAction(plugin, "emakiitem_repair_amount", ItemHeldItemAction.Operation.REPAIR_AMOUNT));
        registry.register(plugin, "emakiitem", new ItemHeldItemAction(plugin, "emakiitem_damage", ItemHeldItemAction.Operation.DAMAGE));
        registry.register(plugin, "emakiitem", new ItemHeldItemAction(plugin, "emakiitem_set_damage", ItemHeldItemAction.Operation.SET_DAMAGE));
        registry.register(plugin, "emakiitem", new ItemHeldItemAction(plugin, "emakiitem_set_durability", ItemHeldItemAction.Operation.SET_DURABILITY));
    }
}
