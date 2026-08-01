package emaki.jiuwu.craft.item.action.v2;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageRegistration;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

/**
 * Registers this module's pipeline stages into EmakiCoreLib's single stage registry.
 *
 * <p>Registration is replayed after a CoreLib reload, which rebuilds the stage table; without the rebuild
 * callback these stages would disappear the first time a server owner reloaded.</p>
 */
public final class ItemStageRegistrar {

    private final EmakiItemPlugin plugin;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public ItemStageRegistrar(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    /** Registers every stage and asks to be replayed on reload. Safe to call twice. */
    public void register() {
        closeHandles();
        for (CoreActionStage stage : stages()) {
            CoreStageRegistration registration = EmakiCoreLibApi.registerActionStage(plugin, stage);
            if (registration.successful()) {
                handles.add(registration);
            } else {
                plugin.getLogger().warning("Failed to register pipeline stage '" + stage.id()
                        + "': " + registration.reasonKey());
            }
        }
        EmakiCoreLibApi.onStageRegistryRebuilt(plugin, this::register);
    }

    /** Revokes every stage this registrar installed. */
    public void unregister() {
        closeHandles();
    }

    private List<CoreActionStage> stages() {
        List<CoreActionStage> stages = new ArrayList<>();
        for (ItemHeldItemStage.Operation operation : ItemHeldItemStage.Operation.values()) {
            stages.add(new ItemHeldItemStage(plugin, operation));
        }
        for (ItemComponentStage.Operation operation : ItemComponentStage.Operation.values()) {
            stages.add(new ItemComponentStage(plugin.componentInspector(), operation));
        }
        return List.copyOf(stages);
    }

    private void closeHandles() {
        for (CoreStageRegistration handle : handles) {
            handle.close();
        }
        handles.clear();
    }
}
