package emaki.jiuwu.craft.strengthen.action;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class StrengthenStageRegistrar {

    private final EmakiStrengthenPlugin plugin;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public StrengthenStageRegistrar(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        closeHandles();
        for (StrengthenHeldItemStage.Operation operation : StrengthenHeldItemStage.Operation.values()) {
            CoreStageRegistration registration = EmakiCoreLibApi.registerActionStage(
                    plugin, new StrengthenHeldItemStage(plugin, operation));
            if (registration.successful()) {
                handles.add(registration);
            } else {
                plugin.getLogger().warning("Failed to register pipeline stage '" + operation.id()
                        + "': " + registration.reasonKey());
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
