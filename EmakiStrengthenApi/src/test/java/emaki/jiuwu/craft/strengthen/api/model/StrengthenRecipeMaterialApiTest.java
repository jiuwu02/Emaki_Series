package emaki.jiuwu.craft.strengthen.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

public final class StrengthenRecipeMaterialApiTest {

    @Test
    void preservesLegacyMaterialView() {
        StrengthenRecipe.StarStageMaterial legacy = new StrengthenRecipe.StarStageMaterial(
                "minecraft-diamond", 2, false, true, 3);
        assertEquals("minecraft-diamond", legacy.item());
        assertEquals("minecraft-diamond", legacy.materialId());
        assertEquals("minecraft-diamond", legacy.countKey());
        assertEquals(List.of("minecraft-diamond"), legacy.itemSources());
        org.junit.jupiter.api.Assertions.assertTrue(legacy.legacyInput());
    }

    @Test
    void keepsAttemptAuditCoordinatesSeparateFromStableIdentity() {
        AttemptMaterial material = new AttemptMaterial("display-item", 2, 2, false, false, 0, 2,
                "stable_material", "shared_count", 7, "audit-source");

        assertEquals("stable_material", material.materialId());
        assertEquals("shared_count", material.countKey());
        assertEquals(7, material.inputIndex());
        assertEquals("audit-source", material.sourceToken());
    }

    @Test
    void exposesCanonicalMaterialView() {
        StrengthenRecipe.StarStageMaterial canonical = new StrengthenRecipe.StarStageMaterial(
                "flame_gem", "shared_gems", List.of("minecraft-amethyst_shard"),
                List.of("matcher"), 1, true, false, 4);
        assertEquals("flame_gem", canonical.materialId());
        assertEquals("shared_gems", canonical.countKey());
        assertEquals(List.of("minecraft-amethyst_shard"), canonical.sources());
        assertEquals(List.of("matcher"), canonical.matcherConfig());
    }
}
