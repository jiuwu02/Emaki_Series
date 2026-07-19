package emaki.jiuwu.craft.item.action;

import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreAction;
import emaki.jiuwu.craft.corelib.api.action.CoreActionRegistration;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemActionRegistrar {

    private final EmakiItemPlugin plugin;

    public ItemActionRegistrar(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(ActionRegistry registry) {
        if (registry != null) {
            registry.register(plugin, "emakiitem", new ItemHeldItemAction(
                    plugin, "emakiitem_update", ItemHeldItemAction.Operation.UPDATE));
            registry.register(plugin, "emakiitem", new ItemHeldItemAction(
                    plugin, "emakiitem_rerender", ItemHeldItemAction.Operation.RERENDER));
            registry.register(plugin, "emakiitem", new ItemHeldItemAction(
                    plugin, "emakiitem_repair_amount", ItemHeldItemAction.Operation.REPAIR_AMOUNT));
            registry.register(plugin, "emakiitem", new ItemHeldItemAction(
                    plugin, "emakiitem_damage", ItemHeldItemAction.Operation.DAMAGE));
            registry.register(plugin, "emakiitem", new ItemHeldItemAction(
                    plugin, "emakiitem_set_damage", ItemHeldItemAction.Operation.SET_DAMAGE));
            registry.register(plugin, "emakiitem", new ItemHeldItemAction(
                    plugin, "emakiitem_set_durability", ItemHeldItemAction.Operation.SET_DURABILITY));
        }
        registerCoreAction(new ItemComponentAction(plugin.componentInspector(), ItemComponentAction.Operation.ADD));
        registerCoreAction(new ItemComponentAction(plugin.componentInspector(), ItemComponentAction.Operation.MODIFY));
        registerCoreAction(new ItemComponentAction(plugin.componentInspector(), ItemComponentAction.Operation.REMOVE));
    }

    private void registerCoreAction(CoreAction action) {
        CoreActionRegistration registration = EmakiCoreLibApi.registerAction(plugin, "emakiitem", action);
        if (!registration.registered()) {
            plugin.getLogger().warning("Failed to register CoreLib action '" + action.id() + "': "
                    + registration.result().errorMessage());
        }
    }
}
