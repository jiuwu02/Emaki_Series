package emaki.jiuwu.craft.cooking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.cooking.model.CookingInputIngredient;

class CookingInputIngredientTest {

    @Test
    void canonicalMapKeepsSlotCountSourcesMatcherAndAmount() {
        Map<String, Object> matcher = Map.of(
                "type", "component",
                "component", "custom_name",
                "operator", "contains",
                "value", "aged");
        CookingInputIngredient input = new CookingInputIngredient(
                "minecraft-apple",
                3,
                "aged_apple_slot",
                "fruit",
                List.of("minecraft-apple"),
                matcher);

        assertEquals(Map.of(
                "source", "minecraft-apple",
                "slot_id", "aged_apple_slot",
                "count_key", "fruit",
                "item_sources", List.of("minecraft-apple"),
                "matcher", matcher,
                "amount", 3), input.toMap());
    }
}
