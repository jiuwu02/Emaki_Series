package emaki.jiuwu.craft.forge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.forge.model.ForgeMaterialIdentity;

class ForgeAuditIdentityResolverTest {
    @Test
    void identitiesRemainIndependent() {
        ForgeMaterialIdentity identity = ForgeMaterialIdentity.resolve(
                "selection", "consumption", "refresh", "source", "matcher");
        assertEquals("selection", identity.materialId());
        assertEquals("consumption", identity.countKey());
        assertEquals("refresh", identity.auditId());
    }

    @Test
    void sameSourceDifferentMatcherRemainsDistinct() {
        ForgeMaterialIdentity first = ForgeMaterialIdentity.resolve("iron_red", "iron", "audit_red", "iron", "red");
        ForgeMaterialIdentity second = ForgeMaterialIdentity.resolve("iron_blue", "iron", "audit_blue", "iron", "blue");
        assertFalse(first.materialId().equals(second.materialId()));
        assertEquals(first.countKey(), second.countKey());
    }

    @Test
    void differentSourcesCanShareCountKey() {
        ForgeMaterialIdentity first = ForgeMaterialIdentity.resolve("iron", "metal", "iron_audit", "iron", "");
        ForgeMaterialIdentity second = ForgeMaterialIdentity.resolve("gold", "metal", "gold_audit", "gold", "");
        Map<String, Integer> totals = ForgeAllocationMath.aggregate(
                Map.of(first.materialId(), 2, second.materialId(), 3),
                Map.of(first.materialId(), first.countKey(), second.materialId(), second.countKey()));
        assertEquals(5, totals.get("metal"));
    }

    @Test
    void requiredAndOptionalAllocationAreSeparated() {
        assertEquals(Map.of("required", 3), ForgeAllocationMath.requiredConsumption(Map.of("required", 3)));
        assertEquals(Map.of("optional", 4), ForgeAllocationMath.optionalConsumption(
                Map.of("optional", 2), Map.of("optional", 5)));
    }

    @Test
    void canonicalEntrySeparatesMaterialCountAndAuditIdentity() {
        Map<String, Object> entry = ForgeAuditIdentityResolver.canonicalEntry(
                "variant_a", "shared_count", "audit_a", "minecraft-iron_ingot",
                "required", 3, 3, 4, 2, "minecraft-iron_ingot", "matcher-a");
        assertEquals("variant_a", entry.get("material_id"));
        assertEquals("shared_count", entry.get("count_key"));
        assertEquals("audit_a", entry.get("audit_id"));
        assertEquals(3, entry.get("amount_consumed"));
        assertEquals("matcher-a", entry.get("matcher"));
        assertEquals(Map.of("slot", 4, "amount", 3), entry.get("allocation"));
    }

    @Test
    void resolverUsesAuditThenMaterialThenCountThenLegacy() {
        Map<String, Object> entry = Map.of(
                "audit_id", "missing-audit",
                "material_id", "material-a",
                "count_key", "shared-count",
                "material_item", "legacy-item",
                "amount", 2);
        ForgeAuditIdentityResolver.Resolution<String> resolution = ForgeAuditIdentityResolver.resolve(
                entry,
                value -> null,
                value -> "material:" + value,
                value -> "count:" + value,
                value -> "legacy:" + value);
        assertEquals("material:material-a", resolution.value());
        assertEquals("material_id", resolution.mode());
        assertTrue(resolution.requiresMigration());
        assertEquals(2, resolution.fields().contributionAmount());
        assertEquals(2, resolution.fields().amountConsumed());
    }

    @Test
    void resolverFallsBackToLegacyAndMarksMigration() {
        ForgeAuditIdentityResolver.Resolution<String> resolution = ForgeAuditIdentityResolver.resolve(
                Map.of("material_item", "old-source", "amount", 1),
                value -> null, value -> null, value -> null, value -> value);
        assertEquals("old-source", resolution.value());
        assertEquals("legacy", resolution.mode());
        assertTrue(resolution.requiresMigration());
    }

    @Test
    void canonicalResolutionDoesNotRequireMigration() {
        ForgeAuditIdentityResolver.Resolution<String> resolution = ForgeAuditIdentityResolver.resolve(
                Map.of("audit_id", "audit-a", "material_id", "material-a", "count_key", "count-a"),
                value -> value, value -> null, value -> null, value -> null);
        assertEquals("audit-a", resolution.value());
        assertFalse(resolution.requiresMigration());
    }

    @Test
    void unresolvedRefreshKeepsFailureFallback() {
        ForgeAuditIdentityResolver.Resolution<String> resolution = ForgeAuditIdentityResolver.resolve(
                Map.of("audit_id", "missing", "material_id", "missing", "count_key", "missing"),
                value -> null, value -> null, value -> null, value -> null);
        assertEquals(null, resolution.value());
        assertFalse(resolution.requiresMigration());
    }
}
