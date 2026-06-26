package emaki.jiuwu.craft.cooking.model;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.item.ItemSource;

/**
 * 一条营养食物来源规则：吃下匹配 {@link #itemSources()} 中任意来源的物品时，
 * 按 {@link #nutrition()} 给玩家增加对应营养值，并执行可选的额外动作。
 *
 * @param itemSources 允许触发的物品来源（任意命中即生效）
 * @param nutrition   营养类型 id 到增加值的映射
 * @param actions     命中后额外执行的 CoreLib 动作行
 */
public record NutritionFoodSource(List<ItemSource> itemSources,
        Map<String, Double> nutrition,
        List<String> actions) {

    public NutritionFoodSource {
        itemSources = itemSources == null ? List.of() : List.copyOf(itemSources);
        nutrition = nutrition == null ? Map.of() : Map.copyOf(nutrition);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
