package emaki.jiuwu.craft.codex.action;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

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
                plugin.getLogger().warning("Failed to register pipeline stage '" + stage.id()
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
