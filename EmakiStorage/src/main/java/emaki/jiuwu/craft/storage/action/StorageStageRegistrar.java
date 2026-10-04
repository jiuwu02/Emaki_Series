package emaki.jiuwu.craft.storage.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.storage.EmakiStoragePlugin;

public final class StorageStageRegistrar {

    private final EmakiStoragePlugin plugin;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public StorageStageRegistrar(EmakiStoragePlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        closeHandles();
        for (StorageStage.Operation operation : StorageStage.Operation.values()) {
            CoreStageRegistration registration = EmakiCoreLibApi.registerActionStage(
                    plugin, new StorageStage(plugin, operation));
            if (registration.successful()) {
                handles.add(registration);
            } else {
                plugin.messageService().warning("console.stage_register_failed", Map.of(
                        "stage", operation.id(),
                        "reason", String.valueOf(registration.reasonKey())));
            }
        }
        EmakiCoreLibApi.onStageRegistryRebuilt(plugin, this::register);
    }

    public void unregister() {
        closeHandles();
    }

    private void closeHandles() {
        for (CoreStageRegistration handle : handles) {
            handle.close();
        }
        handles.clear();
    }
}
