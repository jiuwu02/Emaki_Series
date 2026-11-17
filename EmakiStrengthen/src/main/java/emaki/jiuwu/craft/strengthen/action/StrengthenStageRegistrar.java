package emaki.jiuwu.craft.strengthen.action;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.Map;
import java.util.logging.Level;

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
                LogTags.send(plugin, LogTag.STARTUP, Level.WARNING, "startup.stage_register_failed", Map.of("stage", operation.id(), "reason", registration.reasonKey()));
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
