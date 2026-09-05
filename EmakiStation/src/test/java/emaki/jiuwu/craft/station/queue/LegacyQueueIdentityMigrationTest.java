package emaki.jiuwu.craft.station.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

class LegacyQueueIdentityMigrationTest {

    @Test
    void readsLegacyQueueFixtureAndAddsReceiptIdentities() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/legacy/station-queue-v1.yml")) {
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(text.contains("state: waiting"));
            Map<String, Object> root = new LinkedHashMap<>(YamlFiles.load(text).asMap());
            Map<?, ?> stations = (Map<?, ?>) root.get("stations");
            Map<?, ?> entry = (Map<?, ?>) ((List<?>) stations.get("blacksmith")).getFirst();
            Map<String, Object> legacy = new LinkedHashMap<>();
            entry.forEach((key, value) -> legacy.put(String.valueOf(key), value));
            Map<String, Object> canonical = QueueStore.migrateEntry(legacy);
            assertEquals(2, canonical.get("schema"));
            assertEquals("iron_sword", canonical.get("recipe_identity"));
            Map<?, ?> receipt = (Map<?, ?>) ((List<?>) canonical.get("consumed")).getFirst();
            assertEquals("minecraft-iron_ingot", receipt.get("material_id"));
            assertEquals("minecraft-iron_ingot", receipt.get("count_key"));
            assertEquals("minecraft-iron_ingot", receipt.get("requirement_id"));
            assertEquals("minecraft-iron_ingot", receipt.get("matched_source"));
        }
    }

    @Test
    void replacesPersistedLegacyReceiptIdentityWithMatchedSource() {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("source", "minecraft-iron_ingot");
        receipt.put("material_id", "legacy");
        receipt.put("requirement_id", "legacy");
        receipt.put("count_key", "legacy");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("recipe", "iron_sword");
        entry.put("consumed", List.of(receipt));

        Map<String, Object> canonical = QueueStore.migrateEntry(entry);
        Map<?, ?> migrated = (Map<?, ?>) ((List<?>) canonical.get("consumed")).getFirst();

        assertEquals("minecraft-iron_ingot", migrated.get("material_id"));
        assertEquals("minecraft-iron_ingot", migrated.get("requirement_id"));
        assertEquals("minecraft-iron_ingot", migrated.get("count_key"));
    }
}
