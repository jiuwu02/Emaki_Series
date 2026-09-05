package emaki.jiuwu.craft.forge.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ForgeMaterialParserIdentityTest {
    @Test
    void explicitIdentityFieldsRemainIndependent() {
        ForgeMaterial material = ForgeMaterial.fromConfig(Map.of(
                "material_id", "selection_form",
                "count_key", "shared_consumption",
                "audit_id", "refresh_form",
                "item_sources", List.of("minecraft-iron_ingot"),
                "amount", 2));

        assertEquals("selection_form", material.materialId());
        assertEquals("shared_consumption", material.countKey());
        assertEquals("refresh_form", material.auditId());
        assertTrue(material.materialIdDeclared());
        assertTrue(material.countKeyDeclared());
        assertTrue(material.auditIdDeclared());
    }

    @Test
    void missingIdentityFieldsRemainLegacyOnly() {
        ForgeMaterial material = ForgeMaterial.fromConfig(Map.of(
                "item_sources", List.of("minecraft-iron_ingot"),
                "amount", 1));

        assertFalse(material.materialIdDeclared());
        assertFalse(material.countKeyDeclared());
        assertFalse(material.auditIdDeclared());
        assertEquals("minecraft-iron_ingot", material.legacySourceKey());
        Map<String, Object> signature = material.definitionSignatureData();
        assertFalse(signature.containsKey("material_id"));
        assertFalse(signature.containsKey("count_key"));
        assertFalse(signature.containsKey("audit_id"));
        assertEquals("minecraft-iron_ingot", signature.get("legacy_identity"));
    }

    @Test
    void sixDefaultMaterialIdentitiesRemainIndependent() {
        List<ForgeMaterialIdentity> identities = List.of(
                ForgeMaterialIdentity.resolve("iron", "iron", "iron_audit", "iron", ""),
                ForgeMaterialIdentity.resolve("blaze", "blaze", "blaze_audit", "blaze", ""),
                ForgeMaterialIdentity.resolve("fire", "fire", "fire_audit", "fire", ""),
                ForgeMaterialIdentity.resolve("feather", "feather", "feather_audit", "feather", ""),
                ForgeMaterialIdentity.resolve("echo", "echo", "echo_audit", "echo", ""),
                ForgeMaterialIdentity.resolve("star", "star", "star_audit", "star", ""));

        assertEquals(6, identities.stream().map(ForgeMaterialIdentity::materialId).distinct().count());
        assertEquals(6, identities.stream().map(ForgeMaterialIdentity::countKey).distinct().count());
        assertEquals(6, identities.stream().map(ForgeMaterialIdentity::auditId).distinct().count());
    }

    @Test
    void sameSourceFormsCanUseDifferentMatcherAndSharedCountKey() {
        ForgeMaterial first = ForgeMaterial.fromConfig(Map.of(
                "material_id", "iron_red",
                "count_key", "iron",
                "audit_id", "iron_red_audit",
                "item_sources", List.of("minecraft-iron_ingot"),
                "matcher", Map.of("type", "component", "component", "custom_name",
                        "operator", "contains", "value", "red")));
        ForgeMaterial second = ForgeMaterial.fromConfig(Map.of(
                "material_id", "iron_blue",
                "count_key", "iron",
                "audit_id", "iron_blue_audit",
                "item_sources", List.of("minecraft-iron_ingot"),
                "matcher", Map.of("type", "component", "component", "custom_name",
                        "operator", "contains", "value", "blue")));

        assertEquals("iron", first.countKey());
        assertEquals(first.countKey(), second.countKey());
        assertFalse(first.materialId().equals(second.materialId()));
        assertFalse(first.auditId().equals(second.auditId()));
    }
}
