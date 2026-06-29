package emaki.jiuwu.craft.cooking.action;

import java.util.List;

import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;

/**
 * 把营养增/减/设置动作注册到 CoreLib 的 ActionRegistry。
 *
 * <p>source 统一为 {@code emakicooking}，便于插件禁用时 {@code unregisterAll(plugin)} 批量卸载。
 * 每种操作注册多个别名 id（参考 EmakiLevel 的 {@code LevelActionRegistrar}）。</p>
 */
public final class NutritionActionRegistrar {

    private final EmakiCookingPlugin plugin;

    public NutritionActionRegistrar(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(ActionRegistry registry) {
        if (registry == null) {
            return;
        }
        register(registry, NutritionOperationType.ADD, List.of("emakicookingaddnutrition", "ecaddnutrition", "cookingaddnutrition"));
        register(registry, NutritionOperationType.REMOVE, List.of("emakicookingremovenutrition", "ecremovenutrition", "ectakenutrition", "cookingremovenutrition"));
        register(registry, NutritionOperationType.SET, List.of("emakicookingsetnutrition", "ecsetnutrition", "cookingsetnutrition"));
    }

    private void register(ActionRegistry registry, NutritionOperationType type, List<String> ids) {
        for (String id : ids) {
            registry.register(plugin, "emakicooking", new NutritionOperationAction(plugin, id, type));
        }
    }
}
