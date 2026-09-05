package emaki.jiuwu.craft.cooking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;

class FermentationBarrelStateCodecTest {

    private final FermentationBarrelStateCodec codec = new FermentationBarrelStateCodec();

    @Test
    void legacySlotsRequireIdentityMigration() {
        FermentationBarrelState state = codec.readState(new MapYamlSection(Map.of(
                "station_type", "fermentation_barrel",
                "fermentation_barrel", Map.of("active_recipe_id", "apple_cider"),
                "gui_slots", List.of(Map.of(
                        "index", 10,
                        "source", "minecraft-apple",
                        "amount", 3,
                        "item", Map.of("type", "APPLE"))))));

        assertTrue(state.valid());
        assertTrue(state.requiresIdentityMigration());
        assertFalse(state.slotIdsResolved());
        assertEquals("minecraft-apple", state.slotSources().get(10));
    }

    @Test
    void canonicalSerializationWritesSchemaAndBothIdentities() {
        FermentationBarrelState state = new FermentationBarrelState();
        state.setSlot(10, "fruit_slot", "fruit", "minecraft-apple", Map.of("type", "APPLE"), 3);

        Map<String, Object> serialized = codec.serializeState(new StationCoordinates("world", 1, 2, 3), state);
        Map<?, ?> slot = (Map<?, ?>) ((List<?>) serialized.get("gui_slots")).get(0);

        assertEquals(2, serialized.get("schema_version"));
        assertEquals("fruit_slot", slot.get("slot_id"));
        assertEquals("fruit", slot.get("count_key"));
        assertEquals("minecraft-apple", slot.get("source"));
    }

    @Test
    void oldSchemaWithCanonicalSlotsIsMarkedForWriteback() {
        FermentationBarrelState state = codec.readState(new MapYamlSection(Map.of(
                "station_type", "fermentation_barrel",
                "schema_version", 1,
                "fermentation_barrel", Map.of("active_recipe_id", "apple_cider"),
                "gui_slots", List.of(Map.of(
                        "index", 10,
                        "slot_id", "fruit_slot",
                        "count_key", "fruit",
                        "source", "minecraft-apple",
                        "amount", 3,
                        "item", Map.of("type", "APPLE"))))));

        assertTrue(state.valid());
        assertTrue(state.slotIdsResolved());
        assertTrue(state.needsSchemaWriteback());
        assertEquals(2, codec.serializeState(new StationCoordinates("world", 1, 2, 3), state).get("schema_version"));
    }

    @Test
    void duplicateIndexesAreInvalid() {
        Map<String, Object> first = Map.of("index", 10, "source", "minecraft-apple", "amount", 1);
        Map<String, Object> second = Map.of("index", 10, "source", "minecraft-sugar", "amount", 1);
        FermentationBarrelState state = codec.readState(new MapYamlSection(Map.of(
                "station_type", "fermentation_barrel",
                "fermentation_barrel", Map.of("active_recipe_id", "apple_cider"),
                "gui_slots", List.of(first, second))));

        assertFalse(state.valid());
    }

    @Test
    void duplicateCanonicalSlotIdsAreInvalid() {
        Map<String, Object> first = Map.of(
                "index", 10,
                "slot_id", "fruit_slot",
                "count_key", "fruit",
                "source", "minecraft-apple",
                "amount", 1,
                "item", Map.of("type", "APPLE"));
        Map<String, Object> second = Map.of(
                "index", 11,
                "slot_id", "fruit_slot",
                "count_key", "fruit",
                "source", "minecraft-apple",
                "amount", 1,
                "item", Map.of("type", "APPLE"));
        FermentationBarrelState state = codec.readState(new MapYamlSection(Map.of(
                "station_type", "fermentation_barrel",
                "schema_version", 2,
                "fermentation_barrel", Map.of(),
                "gui_slots", List.of(first, second))));

        assertFalse(state.valid());
    }

    @Test
    void currentSchemaMissingCountKeyIsInvalid() {
        FermentationBarrelState state = codec.readState(new MapYamlSection(Map.of(
                "station_type", "fermentation_barrel",
                "schema_version", 2,
                "fermentation_barrel", Map.of("active_recipe_id", "apple_cider"),
                "gui_slots", List.of(Map.of(
                        "index", 10,
                        "slot_id", "fruit_slot",
                        "source", "minecraft-apple",
                        "amount", 3,
                        "item", Map.of("type", "APPLE"))))));

        assertFalse(state.valid());
    }
}
