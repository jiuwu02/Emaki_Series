package emaki.jiuwu.craft.item.action;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemStageRegistrar {

    private final EmakiItemPlugin plugin;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public ItemStageRegistrar(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

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
