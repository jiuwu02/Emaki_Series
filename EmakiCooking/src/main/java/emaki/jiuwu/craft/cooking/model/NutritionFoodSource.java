package emaki.jiuwu.craft.cooking.model;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.item.ItemSource;









public record NutritionFoodSource(List<ItemSource> itemSources,
        Map<String, Double> nutrition,
        List<String> actions) {

    public NutritionFoodSource {
        itemSources = itemSources == null ? List.of() : List.copyOf(itemSources);
        nutrition = nutrition == null ? Map.of() : Map.copyOf(nutrition);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
