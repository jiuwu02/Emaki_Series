package emaki.jiuwu.craft.strengthen.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

class StrengthenLegacyFixtureTest {

    @Test
    void readsCanonicalRecipeFixtureWithStableMaterialIdentity() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/recipes/example_recipe.yml")) {
            Map<?, ?> root = YamlFiles.load(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).asMap();
            assertEquals(2, root.get("schema_version"));
            Map<?, ?> stars = (Map<?, ?>) root.get("stars");
            Map<?, ?> starOne = (Map<?, ?>) stars.get("1");
            Map<?, ?> material = (Map<?, ?>) ((List<?>) starOne.get("materials")).get(2);
            assertEquals("flame_gold_nugget", material.get("material_id"));
            assertEquals("flame_gold_nugget", material.get("count_key"));
        }
    }

    @Test
    void readsLegacyStarStageFixtureAndCanonicalizesMaterial() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/legacy/strengthen-star-stage-v1.yml")) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("star: 3"));
            Map<String, Object> legacy = new LinkedHashMap<>();
            Map<?, ?> root = YamlFiles.load(text).asMap();
            legacy.put("item", ((Map<?, ?>) ((List<?>) root.get("materials")).getFirst()).get("item"));
            legacy.put("amount", ((Map<?, ?>) ((List<?>) root.get("materials")).getFirst()).get("amount"));
            Map<String, Object> canonical = StrengthenMaterialSchema.canonicalize(legacy, 0);
            assertEquals("minecraft-diamond", canonical.get("material_id"));
            assertEquals("minecraft-diamond", canonical.get("count_key"));
            assertEquals("minecraft-diamond", canonical.get("item_sources"));
        }
    }
}
