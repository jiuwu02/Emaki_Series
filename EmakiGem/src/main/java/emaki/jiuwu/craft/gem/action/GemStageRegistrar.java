package emaki.jiuwu.craft.gem.action;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.service.GemRerollSessionService;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.Map;
import java.util.logging.Level;

public final class GemStageRegistrar {

    private final EmakiGemPlugin plugin;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public GemStageRegistrar(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        closeHandles();
        for (GemHeldItemStage.Operation operation : GemHeldItemStage.Operation.values()) {
            CoreStageRegistration registration = EmakiCoreLibApi.registerActionStage(
                    plugin, new GemHeldItemStage(plugin, operation));
            if (registration.successful()) {
                handles.add(registration);
            } else {
                LogTags.send(plugin, LogTag.STARTUP, Level.WARNING, "startup.register_pipeline_stage_failed", Map.of("stage", operation.id(), "reason", registration.reasonKey()));
            }
        }
        EmakiCoreLibApi.onStageRegistryRebuilt(plugin, this::rebuild);
    }

    private void rebuild() {
        if (plugin.rerollSessionService() != null) {
            plugin.rerollSessionService().clearAll(GemRerollSessionService.TerminationReason.RELOAD);
        }
        register();
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
