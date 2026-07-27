package emaki.jiuwu.craft.cooking.action;

import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;






public final class NutritionActionRegistrar {

    private final EmakiCookingPlugin plugin;

    public NutritionActionRegistrar(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(ActionRegistry registry) {
        if (registry == null) {
            return;
        }
        register(registry, NutritionOperationType.ADD, "emakicookingaddnutrition");
        register(registry, NutritionOperationType.REMOVE, "emakicookingremovenutrition");
        register(registry, NutritionOperationType.SET, "emakicookingsetnutrition");
        registerReset(registry, NutritionResetAction.Mode.CLEAR, "emakicookingclearnutrition");
        registerReset(registry, NutritionResetAction.Mode.RESET, "emakicookingresetnutrition");
        registerThresholdRecheck(registry, "emakicookingrechecknutritionthreshold");
        registerRecipeReward(registry, "emakicookingrunrecipereward");
    }

    private void register(ActionRegistry registry, NutritionOperationType type, String id) {
        registry.register(plugin, "emakicooking", new NutritionOperationAction(plugin, id, type));
    }

    private void registerReset(ActionRegistry registry, NutritionResetAction.Mode mode, String id) {
        registry.register(plugin, "emakicooking", new NutritionResetAction(plugin, id, mode));
    }

    private void registerThresholdRecheck(ActionRegistry registry, String id) {
        registry.register(plugin, "emakicooking", new NutritionThresholdRecheckAction(plugin, id));
    }

    private void registerRecipeReward(ActionRegistry registry, String id) {
        registry.register(plugin, "emakicooking", new RunRecipeRewardAction(plugin, id));
    }
}
