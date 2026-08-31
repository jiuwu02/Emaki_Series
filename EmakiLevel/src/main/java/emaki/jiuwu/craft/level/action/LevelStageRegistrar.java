package emaki.jiuwu.craft.level.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.api.LevelOperationType;

public final class LevelStageRegistrar {

    private static final Map<LevelOperationType, String> STAGE_IDS = stageIds();

    private final EmakiLevelPlugin plugin;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public LevelStageRegistrar(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    private static Map<LevelOperationType, String> stageIds() {
        Map<LevelOperationType, String> ids = new LinkedHashMap<>();
        ids.put(LevelOperationType.ADD_EXP, "level_add_exp");
        ids.put(LevelOperationType.SET_EXP, "level_set_exp");
        ids.put(LevelOperationType.REMOVE_EXP, "level_remove_exp");
        ids.put(LevelOperationType.ADD_LEVEL, "level_add_level");
        ids.put(LevelOperationType.SET_LEVEL, "level_set_level");
        ids.put(LevelOperationType.REMOVE_LEVEL, "level_remove_level");
        ids.put(LevelOperationType.RESET, "level_reset");
        ids.put(LevelOperationType.LEVEL_UP, "level_up");
        return Map.copyOf(ids);
    }

    public void register() {
        closeHandles();
        STAGE_IDS.forEach((operation, id) -> {
            CoreStageRegistration registration = EmakiCoreLibApi.registerActionStage(
                    plugin, new LevelOperationStage(plugin, operation, id));
            if (registration.successful()) {
                handles.add(registration);
            } else {
                plugin.getLogger().warning("Failed to register pipeline stage '" + id
                        + "': " + registration.reasonKey());
            }
        });
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
