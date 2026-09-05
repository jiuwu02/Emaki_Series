package emaki.jiuwu.craft.forge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.forge.model.ForgeMaterialIdentity;

class ForgeLegacyFixtureTest {

    @Test
    void readsLegacyAuditFixtureAndDerivesIdentities() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/legacy/forge-audit-v1.yml")) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("material_item:"));
            Map<String, Object> legacy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : YamlFiles.load(text).getMapList("materials").getFirst().entrySet()) {
                if (entry.getKey() != null) {
                    legacy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            ForgeAuditIdentityResolver.Resolution<String> resolution = ForgeAuditIdentityResolver.resolve(
                    legacy, value -> null, value -> null, value -> null, value -> value);
            ForgeMaterialIdentity identity = ForgeMaterialIdentity.resolve(
                    resolution.fields().materialId(), resolution.fields().countKey(),
                    resolution.fields().auditId(), resolution.fields().legacyMaterial(), "");
            assertEquals("minecraft-iron_ingot", identity.materialId());
            assertEquals("minecraft-iron_ingot", identity.countKey());
            assertEquals("minecraft-iron_ingot", identity.auditId());
            assertEquals(3, resolution.fields().contributionAmount());
        }
    }
}
