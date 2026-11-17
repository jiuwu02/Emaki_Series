package emaki.jiuwu.craft.codex.action;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.Map;
import java.util.logging.Level;

public final class CodexStageRegistrar {

    private final EmakiCodexPlugin plugin;
    private final ItemSourceService itemSourceService;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public CodexStageRegistrar(EmakiCodexPlugin plugin, ItemSourceService itemSourceService) {
        this.plugin = plugin;
        this.itemSourceService = itemSourceService;
    }

    public void register() {
        closeHandles();
        List<CoreActionStage> stages = new ArrayList<>();
        for (CodexAdvancementStage.Operation operation : CodexAdvancementStage.Operation.values()) {
            stages.add(new CodexAdvancementStage(plugin, operation));
        }
        stages.add(new ShowAchievementToastStage(plugin, itemSourceService));
        stages.add(new BroadcastAchievementStage(plugin));
        for (CoreActionStage stage : stages) {
            CoreStageRegistration registration = EmakiCoreLibApi.registerActionStage(plugin, stage);
            if (registration.successful()) {
                handles.add(registration);
            } else {
                LogTags.send(plugin, LogTag.STARTUP, Level.WARNING, "startup.pipeline_stage_registration_failed", Map.of("stage", stage.id(), "reason", registration.reasonKey()));
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
