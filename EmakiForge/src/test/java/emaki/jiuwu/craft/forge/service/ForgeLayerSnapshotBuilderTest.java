package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceType;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ForgeLayerSnapshotBuilderTest {

    private final ForgeLayerSnapshotBuilder builder = new ForgeLayerSnapshotBuilder(null);

    @Test
    void materialsSignatureStaysStableForSameDefinition() {
        ForgeMaterial material = material(
            "flame_crystal",
            List.of(
                effect("stat_contribution", Map.of("stats", Map.of("fire_damage", 10))),
                effect("quality_modify", Map.of("mode", "minimum", "tier", "无暇"))
            )
        );
        String first = builder.buildMaterialsSignature(List.of(new ForgeMaterialContribution(material, 1, 0, "optional", 0, material.source())));
        String second = builder.buildMaterialsSignature(List.of(new ForgeMaterialContribution(material, 1, 0, "optional", 0, material.source())));
        assertEquals(first, second);
    }

    @Test
    void materialsSignatureChangesWhenMaterialDefinitionChanges() {
        ForgeMaterial oldMaterial = material(
            "flame_crystal",
            List.of(effect("stat_contribution", Map.of("stats", Map.of("fire_damage", 10))))
        );
        ForgeMaterial newMaterial = material(
            "flame_crystal",
            List.of(
                effect("stat_contribution", Map.of("stats", Map.of("fire_damage", 12))),
                effect("quality_modify", Map.of("mode", "force", "tier", "完美"))
            )
        );
        String oldSignature = builder.buildMaterialsSignature(List.of(new ForgeMaterialContribution(oldMaterial, 1, 0, "optional", 0, oldMaterial.source())));
        String newSignature = builder.buildMaterialsSignature(List.of(new ForgeMaterialContribution(newMaterial, 1, 0, "optional", 0, newMaterial.source())));
        assertNotEquals(oldSignature, newSignature);
    }

    private ForgeMaterial material(String id, List<ForgeMaterial.MaterialEffect> effects) {
        return new ForgeMaterial(
            id,
            id,
            List.of(),
            new ItemSource(ItemSourceType.NEIGEITEMS, "material:" + id),
            0,
            10,
            effects
        );
    }

    private ForgeMaterial.MaterialEffect effect(String type, Map<String, Object> data) {
        return new ForgeMaterial.MaterialEffect(type, data);
    }
}
