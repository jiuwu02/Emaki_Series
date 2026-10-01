package emaki.jiuwu.craft.station.dismantle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.station.recipe.RecipeCost;

public final class DismantleRecipeCostParseTest {

    @Test
    void missingCostSectionIsFreeWithoutDiagnostics() {
        DismantleRecipeLoader loader = new DismantleRecipeLoader(null);
        DismantleRecipeDefinition recipe = loader.parse(null, section(null));
        assertFalse(recipe.cost().charges());
        assertTrue(loader.issues().isEmpty(), "absent cost section must stay silent");
    }

    @Test
    void nestedCurrencyParsesVaultCost() {
        DismantleRecipeDefinition recipe = loader().parse(null, section(Map.of(
                "currency", Map.of("type", "vault", "amount", 100))));
        assertEquals(new RecipeCost(RecipeCost.VAULT, 100L), recipe.cost());
    }

    @Test
    void excellentTokenMapsToExcellentEconomyProvider() {
        DismantleRecipeDefinition recipe = loader().parse(null, section(Map.of(
                "currency", Map.of("type", "excellent", "amount", 5))));
        assertEquals(new RecipeCost(RecipeCost.EXCELLENT, 5L), recipe.cost());
    }

    @Test
    void flatCostWithoutCurrencySectionIsFreeAndReported() {
        DismantleRecipeLoader loader = loader();
        DismantleRecipeDefinition recipe = loader.parse(null, section(
                Map.of("type", "vault", "amount", 100)));
        assertFalse(recipe.cost().charges(), "flat cost must not silently charge");
        List<String> issues = loader.issues();
        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().contains("cost missing currency"));
    }

    @Test
    void nonPositiveAmountIsFreeAndReported() {
        DismantleRecipeLoader loader = loader();
        DismantleRecipeDefinition recipe = loader.parse(null, section(Map.of(
                "currency", Map.of("type", "vault", "amount", 0))));
        assertFalse(recipe.cost().charges());
        List<String> issues = loader.issues();
        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().contains("cost amount"));
    }

    @Test
    void unknownCurrencyTypeIsFreeAndReported() {
        DismantleRecipeLoader loader = loader();
        DismantleRecipeDefinition recipe = loader.parse(null, section(Map.of(
                "currency", Map.of("type", "banana", "amount", 10))));
        assertFalse(recipe.cost().charges());
        List<String> issues = loader.issues();
        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().contains("bad currency"));
    }

    private static DismantleRecipeLoader loader() {
        return new DismantleRecipeLoader(null);
    }

    private static YamlSection section(Map<String, Object> cost) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", "cost_probe");
        root.put("item_sources", List.of("minecraft-iron_block"));
        root.put("pool", List.of(Map.of("item_source", "minecraft-iron_ingot", "amount", 1)));
        if (cost != null) {
            root.put("cost", cost);
        }
        return new MapYamlSection(root);
    }
}
