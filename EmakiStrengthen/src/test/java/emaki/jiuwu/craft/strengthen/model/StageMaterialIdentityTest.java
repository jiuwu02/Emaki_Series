package emaki.jiuwu.craft.strengthen.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public final class StageMaterialIdentityTest {

    @Test
    void keepsMaterialAndCountIdentityStable() {
        StageMaterialIdentity identity = StageMaterialIdentity.resolve(
                "Flame_Gem", "legacy", "Shared_Gem_Count", List.of("minecraft-amethyst_shard"), 2);
        assertEquals("flame_gem", identity.materialId());
        assertEquals("shared_gem_count", identity.countKey());
    }

    @Test
    void appliesLegacyIdentityFallbacksInOrder() {
        assertEquals("legacy_id", StageMaterialIdentity.resolve("", "Legacy_ID", "count", List.of("source"), 0)
                .materialId());
        assertEquals("count_only", StageMaterialIdentity.resolve("", "", "Count_Only", List.of("source"), 0)
                .materialId());
        assertEquals("minecraft-diamond", StageMaterialIdentity.resolve("", "", "",
                List.of("Minecraft-Diamond"), 0).materialId());
        assertEquals("material_4", StageMaterialIdentity.resolve("", "", "", List.of(), 3).materialId());
    }

    @Test
    void indexesRulesByMaterialIdentity() {
        String first = StageMaterialRuleKey.of("fire/left", 3, "ruby");
        String second = StageMaterialRuleKey.of("fire/left", 3, "sapphire");
        assertNotEquals(first, second);
        assertEquals("fire/left@3|ruby", first);
    }

    @Test
    void convertsLegacyItemConservatively() {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("item", "minecraft-diamond");
        legacy.put("amount", 2);
        Map<String, Object> converted = StrengthenMaterialSchema.canonicalize(legacy, 0);
        assertEquals("minecraft-diamond", converted.get("material_id"));
        assertEquals("minecraft-diamond", converted.get("count_key"));
        assertEquals("minecraft-diamond", converted.get("item_sources"));

        Map<String, Object> ambiguous = new LinkedHashMap<>();
        ambiguous.put("item_sources", List.of("minecraft-diamond", "minecraft-emerald"));
        Map<String, Object> rejected = StrengthenMaterialSchema.canonicalize(ambiguous, 1);
        assertTrue(rejected.isEmpty());
        assertEquals(1, ambiguous.size());
        assertTrue(ambiguous.containsKey("item_sources"));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> StrengthenRecipeParser.parseStageMaterials(List.of(ambiguous)));
        assertTrue(exception.getMessage().contains("material_id"));
        assertTrue(exception.getMessage().contains("count_key"));
    }

    @Test
    void canonicalizesCountKeyAndIndexFallbacksWithoutRewritingSource() {
        Map<String, Object> countOnly = new LinkedHashMap<>();
        countOnly.put("count_key", "Shared_Gems");
        Map<String, Object> countCanonical = StrengthenMaterialSchema.canonicalize(countOnly, 0);
        assertEquals("shared_gems", countCanonical.get("material_id"));
        assertEquals("shared_gems", countCanonical.get("count_key"));
        assertEquals(Map.of("count_key", "Shared_Gems"), countOnly);

        Map<String, Object> matcherOnly = new LinkedHashMap<>();
        matcherOnly.put("matcher", Map.of("type", "material", "value", "DIAMOND"));
        Map<String, Object> indexed = StrengthenMaterialSchema.canonicalize(matcherOnly, 2);
        assertFalse(indexed.isEmpty());
        assertEquals("material_3", indexed.get("material_id"));
        assertEquals("material_3", indexed.get("count_key"));
        assertEquals(1, matcherOnly.size());
    }

    @Test
    void acceptsExplicitIdentityForMultipleSources() {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("material_id", "Mixed_Gem");
        material.put("item_sources", List.of("minecraft-diamond", "minecraft-emerald"));

        Map<String, Object> canonical = StrengthenMaterialSchema.canonicalize(material, 0);

        assertEquals("mixed_gem", canonical.get("material_id"));
        assertEquals("mixed_gem", canonical.get("count_key"));
    }
}
