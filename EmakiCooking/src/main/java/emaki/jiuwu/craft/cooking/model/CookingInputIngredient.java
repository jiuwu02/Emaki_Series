package emaki.jiuwu.craft.cooking.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record CookingInputIngredient(String source, int amount, String slotId, String countKey,
        List<String> itemSources, Map<String, Object> matcher, boolean legacy) {

    public CookingInputIngredient {
        source = Texts.toStringSafe(source);
        amount = Math.max(1, amount);
        slotId = Texts.toStringSafe(slotId);
        countKey = Texts.toStringSafe(countKey);
        itemSources = itemSources == null ? List.of() : List.copyOf(itemSources);
        matcher = matcher == null ? Map.of() : Map.copyOf(matcher);
    }

    public CookingInputIngredient(String source, int amount, String slotId, String countKey,
            List<String> itemSources, Map<String, Object> matcher) {
        this(source, amount, slotId, countKey, itemSources, matcher, false);
    }

    public CookingInputIngredient(String source, int amount) {
        this(source, amount, "", "", source == null || source.isBlank() ? List.of() : List.of(source), Map.of(), true);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", source);
        map.put("amount", amount);
        if (legacy) {
            map.put("legacy", true);
            return map;
        }
        map.put("slot_id", slotId);
        map.put("count_key", countKey);
        map.put("item_sources", itemSources);
        if (!matcher.isEmpty()) {
            map.put("matcher", matcher);
        }
        return map;
    }
}
