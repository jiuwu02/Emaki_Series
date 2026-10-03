package emaki.jiuwu.craft.item.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemEditFlowTest {

    private static final ItemDefinitionDocument.DraftValidator STRUCTURAL =
            (candidate, itemId, source) -> {
                Object rawId = candidate.value("id");
                if (rawId == null || String.valueOf(rawId).isBlank()) {
                    return "definition_parse_failed";
                }
                return itemId.equals(String.valueOf(rawId)) ? null : "id_changed";
            };

    private final Logger logger = Logger.getLogger("ItemEditFlowTest");

    @Test
    void createdSkeletonExposesEveryEditableNode() {
        YamlTextDocument document = YamlTextDocument.parse(ItemEditorGuiService.skeleton("demo_item"));
        assertEquals("demo_item", document.value("id"));
        assertEquals("minecraft-stone", document.value("item", "source"));
        assertEquals("all", document.value("equip_slot"));
        assertTrue(document.has("item", "components"));
        assertTrue(document.has("update", "version"));
        assertTrue(document.has("update", "triggers", "join"));
        assertTrue(document.has("set", "id"));
        assertTrue(document.has("condition", "entries"));
        assertTrue(document.has("condition", "on_fail", "message"));
        assertTrue(document.has("repair", "materials"));
        assertTrue(document.has("repair", "economy", "currencies"));
        assertTrue(document.has("repair", "disabled_display", "name_prefix"));
        assertTrue(document.has("actions"));
    }

    @Test
    void referenceScanFindsSetPiecesAndAliasTargets(@TempDir Path directory) throws IOException {
        Files.createDirectories(directory.resolve("sets"));
        Files.writeString(directory.resolve("sets").resolve("s.yml"),
                "id: s\npieces:\n  blade:\n    item: demo\n    slot: main_hand\n", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("id_aliases.yml"),
                "aliases:\n  old:\n    target: demo\n", StandardCharsets.UTF_8);

        List<String> references = ItemEditorInteractionController.collectReferences(directory.toFile(), "demo");

        assertEquals(2, references.size());
        assertTrue(references.stream().anyMatch(entry -> entry.startsWith("sets/s.yml#pieces.blade")));
        assertTrue(references.stream().anyMatch(entry -> entry.startsWith("id_aliases.yml#aliases.old")));
    }

    @Test
    void referenceScanIgnoresUnreferencedItem(@TempDir Path directory) throws IOException {
        Files.createDirectories(directory.resolve("sets"));
        Files.writeString(directory.resolve("sets").resolve("s.yml"),
                "id: s\npieces:\n  blade:\n    item: other\n", StandardCharsets.UTF_8);
        assertEquals(List.of(), ItemEditorInteractionController.collectReferences(directory.toFile(), "demo"));
    }

    @Test
    void repeatedEditsKeepOutputSizeBounded(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("demo.yml");
        Files.writeString(file, ItemEditorGuiService.skeleton("demo"), StandardCharsets.UTF_8);
        ItemDefinitionDocument document = ItemDefinitionDocument.open(logger, file, STRUCTURAL);
        int baseline = Files.readAllLines(file, StandardCharsets.UTF_8).size();

        for (int round = 0; round < 200; round++) {
            final int version = round;
            ItemDefinitionDocument.SaveResult result = document.mutate(
                    candidate -> {
                        candidate.set("demo", "id");
                        candidate.set(version, "update", "version");
                    },
                    directory.resolve("backups"));
            assertTrue(result.saved(), "edit round " + round + " failed: " + result.detail());
        }

        int after = Files.readAllLines(file, StandardCharsets.UTF_8).size();
        assertTrue(after <= baseline + 8, "repeated edits must not inflate the document: " + baseline + " -> " + after);
        assertEquals("demo", String.valueOf(document.view().value("id")));
    }

    @Test
    void largeDocumentEditStaysWithinTimeBudget() {
        StringBuilder builder = new StringBuilder("id: big\nitem:\n  source: minecraft-stone\n");
        for (int index = 0; index < 400; index++) {
            builder.append("  c").append(index).append(": ").append(index).append('\n');
        }
        YamlTextDocument document = YamlTextDocument.parse(builder.toString());
        long started = System.nanoTime();
        for (int round = 0; round < 200; round++) {
            document.set(round, "item", "components", "minecraft:max_stack_size");
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        assertTrue(elapsedMillis < 10_000L, "200 edits on a 400-line document took " + elapsedMillis + "ms");
        assertEquals(199, ((Number) document.value("item", "components", "minecraft:max_stack_size")).intValue());
        assertNotEquals(0, document.text().length());
        assertFalse(document.text().isBlank());
    }
}
