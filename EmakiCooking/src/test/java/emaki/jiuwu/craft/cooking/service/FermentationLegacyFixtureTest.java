package emaki.jiuwu.craft.cooking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

class FermentationLegacyFixtureTest {

    @Test
    void readsLegacyFixtureAndResolvesIdentity() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/legacy/fermentation-state-v1.yml")) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("gui_slots:"));
            FermentationBarrelState state = new FermentationBarrelStateCodec().readState(YamlFiles.load(text));
            assertTrue(state.requiresIdentityMigration());
            assertEquals("minecraft-apple", state.slotSources().get(10));
        }
    }
}
