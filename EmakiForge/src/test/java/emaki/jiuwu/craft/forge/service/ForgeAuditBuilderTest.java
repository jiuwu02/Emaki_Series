package emaki.jiuwu.craft.forge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.forge.model.ForgeMaterial;

class ForgeAuditBuilderTest {
    @Test
    void auditContainsForgeSchemaAndStage() {
        Map<String, Object> audit = new ForgeAuditBuilder().buildAudit(null, List.of(), null, 1D, 42L);

        assertEquals("forge_audit_v2", audit.get("schema"));
        assertEquals(2, audit.get("schema_version"));
        assertEquals("forge", audit.get("stage"));
        assertEquals(42L, audit.get("forged_at"));
        assertEquals(List.of(), audit.get("materials"));
    }

    @Test
    void missingIdentityContributionUsesLegacyDiagnosticOnly() {
        ForgeMaterial material = ForgeMaterial.fromConfig(Map.of(
                "item_sources", List.of("minecraft-iron_ingot"),
                "amount", 1));
        ForgeMaterialContribution contribution = new ForgeMaterialContribution(
                material, 1, 0, "required", 0, material.source());

        Map<String, Object> audit = contribution.toAuditMap();
        assertFalse(audit.containsKey("material_id"));
        assertFalse(audit.containsKey("count_key"));
        assertFalse(audit.containsKey("audit_id"));
        assertEquals("minecraft-iron_ingot", audit.get("legacy_identity"));
    }
}
