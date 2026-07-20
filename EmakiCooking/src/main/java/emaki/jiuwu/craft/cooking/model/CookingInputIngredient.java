package emaki.jiuwu.craft.cooking.model;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;








public record CookingInputIngredient(String source, int amount) {

    public CookingInputIngredient {
        source = Texts.toStringSafe(source);
        amount = Math.max(1, amount);
    }


    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", source);
        map.put("amount", amount);
        return map;
    }
}
