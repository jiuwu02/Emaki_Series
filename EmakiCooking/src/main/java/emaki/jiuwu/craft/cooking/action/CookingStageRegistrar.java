package emaki.jiuwu.craft.cooking.action;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;

public final class CookingStageRegistrar {

    private final EmakiCookingPlugin plugin;
    private final List<CoreStageRegistration> handles = new ArrayList<>();

    public CookingStageRegistrar(EmakiCookingPlugin plugin) {
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
        for (NutritionOperationStage.Operation operation : NutritionOperationStage.Operation.values()) {
            stages.add(new NutritionOperationStage(plugin, operation));
        }
        for (NutritionResetStage.Operation operation : NutritionResetStage.Operation.values()) {
            stages.add(new NutritionResetStage(plugin, operation));
        }
        stages.add(new NutritionThresholdRecheckStage(plugin));
        stages.add(new RunRecipeRewardStage(plugin));
        return List.copyOf(stages);
    }

    private void closeHandles() {
        for (CoreStageRegistration handle : handles) {
            handle.close();
        }
        handles.clear();
    }
}
