package emaki.jiuwu.craft.cooking.model;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.matcher.ItemRequirement;
import emaki.jiuwu.craft.corelib.matcher.Matcher;

public record NutritionFoodSource(List<ItemSourceRef> itemSources,
        Map<String, Double> nutrition,
        List<String> actions,
        Matcher matcher) {

    public NutritionFoodSource {
        itemSources = itemSources == null ? List.of() : List.copyOf(itemSources);
        nutrition = nutrition == null ? Map.of() : Map.copyOf(nutrition);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public ItemRequirement requirement() {
        return new ItemRequirement(itemSources, matcher, ItemRequirement.sourceIdentity(itemSources));
    }

    public NutritionFoodSource(List<ItemSourceRef> itemSources,
            Map<String, Double> nutrition,
            List<String> actions) {
        this(itemSources, nutrition, actions, null);
    }
}
