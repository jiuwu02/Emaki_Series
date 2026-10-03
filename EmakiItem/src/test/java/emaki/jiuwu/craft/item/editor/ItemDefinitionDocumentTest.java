package emaki.jiuwu.craft.item.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemDefinitionDocumentTest {

    private static final String VALID = """
            id: "example_item"

            # 基础物品
            item:
              source: "minecraft-stone"

            equip_slot: "all"
            """;

    private static final ItemDefinitionDocument.DraftValidator STRUCTURAL =
            (candidate, itemId, source) -> {
                Object rawId = candidate.value("id");
                if (rawId == null || String.valueOf(rawId).isBlank()) {
                    return "definition_parse_failed";
                }
                if (!itemId.equals(String.valueOf(rawId))) {
                    return "id_changed";
                }
                return candidate.has("item") ? null : "definition_parse_failed";
            };

    private final Logger logger = Logger.getLogger("ItemDefinitionDocumentTest");

    @Test
    void mutatePersistsChangeAndKeepsComments(@TempDir Path directory) throws IOException {
        Path file = write(directory, "example_item.yml", VALID);
        ItemDefinitionDocument document = open(file);

        ItemDefinitionDocument.SaveResult result =
                document.mutate(candidate -> candidate.set("main_hand", "equip_slot"), directory.resolve("backups"));

        assertEquals(ItemDefinitionDocument.SaveStatus.SAVED, result.status());
        String persisted = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(persisted.contains("# 基础物品"));
        assertTrue(persisted.contains("equip_slot: main_hand"));
    }

    @Test
    void invalidDraftIsRejectedAndFileStaysUntouched(@TempDir Path directory) throws IOException {
        Path file = write(directory, "example_item.yml", VALID);
        ItemDefinitionDocument document = open(file);

        ItemDefinitionDocument.SaveResult result = document.mutate(
                candidate -> {
                    candidate.remove("id");
                    candidate.remove("item");
                },
                directory.resolve("backups"));

        assertEquals(ItemDefinitionDocument.SaveStatus.VALIDATION_FAILED, result.status());
        assertEquals(VALID, Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("all", currentEquipSlot(document));
    }

    @Test
    void externallyModifiedFileIsRejected(@TempDir Path directory) throws IOException, InterruptedException {
        Path file = write(directory, "example_item.yml", VALID);
        ItemDefinitionDocument document = open(file);
        Thread.sleep(15L);
        Files.writeString(file, VALID + "\n# external\n", StandardCharsets.UTF_8);

        ItemDefinitionDocument.SaveResult result =
                document.mutate(candidate -> candidate.set("main_hand", "equip_slot"), directory.resolve("backups"));

        assertEquals(ItemDefinitionDocument.SaveStatus.CONFLICT, result.status());
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("# external"));
    }

    @Test
    void backupIsWrittenBeforeOverwrite(@TempDir Path directory) throws IOException {
        Path file = write(directory, "example_item.yml", VALID);
        Path backupRoot = directory.resolve("backups");
        ItemDefinitionDocument document = open(file);

        document.mutate(candidate -> candidate.set("main_hand", "equip_slot"), backupRoot);

        boolean found;
        try (java.util.stream.Stream<Path> paths = Files.walk(backupRoot)) {
            found = paths.anyMatch(path -> path.getFileName().toString().equals("example_item.yml")
                    && readQuietly(path).contains("equip_slot: \"all\""));
        }
        assertTrue(found);
    }

    @Test
    void idChangeIsRejected(@TempDir Path directory) throws IOException {
        Path file = write(directory, "example_item.yml", VALID);
        ItemDefinitionDocument document = open(file);

        ItemDefinitionDocument.SaveResult result =
                document.mutate(candidate -> candidate.set("other_item", "id"), directory.resolve("backups"));

        assertEquals(ItemDefinitionDocument.SaveStatus.VALIDATION_FAILED, result.status());
        assertEquals("id_changed", result.detail());
    }

    @Test
    void missingPathIsCreatedAndStillValidated(@TempDir Path directory) throws IOException {
        Path file = write(directory, "example_item.yml", VALID);
        ItemDefinitionDocument document = open(file);

        ItemDefinitionDocument.SaveResult result = document.mutate(
                candidate -> candidate.set(Boolean.TRUE, "update", "enabled"),
                directory.resolve("backups"));

        assertEquals(ItemDefinitionDocument.SaveStatus.SAVED, result.status());
        assertNotEquals(VALID, Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("enabled: true"));
    }

    private ItemDefinitionDocument open(Path file) throws IOException {
        return ItemDefinitionDocument.open(logger, file, STRUCTURAL);
    }

    private static String currentEquipSlot(ItemDefinitionDocument document) {
        Object value = document.view().value("equip_slot");
        return value == null ? null : String.valueOf(value);
    }

    private static String readQuietly(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            return "";
        }
    }

    private static Path write(Path directory, String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
