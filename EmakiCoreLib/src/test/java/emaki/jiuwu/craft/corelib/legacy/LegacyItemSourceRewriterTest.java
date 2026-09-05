package emaki.jiuwu.craft.corelib.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

class LegacyItemSourceRewriterTest {

    @Test
    void convertsLegacySourceToItemSourcesInBlockStyle() throws Exception {
        Path root = Files.createTempDirectory("legacy-convert");
        Path file = root.resolve("recipe.yml");
        Files.writeString(file, "input:\n  item_source:\n    - minecraft:iron_ingot\n    - custom:steel\n");
        LegacyTargetSpec spec = LegacyTargetSpec.replace(null, "input", "item_source", "item_sources");

        LegacyItemSourceRewriter.RunReport dry = new LegacyItemSourceRewriter(root, List.of(spec), Logger.getAnonymousLogger()).run(false);

        assertTrue(dry.hasConvertible());
        assertEquals("input:\n  item_source:\n    - minecraft:iron_ingot\n    - custom:steel\n", Files.readString(file));
        assertTrue(dry.files().getFirst().diff().stream().anyMatch(line -> line.startsWith("+   item_sources:")));

        LegacyItemSourceRewriter.RunReport applied = new LegacyItemSourceRewriter(root, List.of(spec), Logger.getAnonymousLogger()).run(true);
        assertTrue(applied.applied());
        String converted = Files.readString(file);
        assertTrue(converted.contains("item_sources:"));
        assertFalse(converted.contains("item_source:"));
        assertTrue(Files.exists(root.resolve("recipe.yml.bak")));

        LegacyItemSourceRewriter.RunReport repeated = new LegacyItemSourceRewriter(root, List.of(spec), Logger.getAnonymousLogger()).run(true);
        assertFalse(repeated.hasConvertible());
        assertEquals(converted, Files.readString(file));
    }

    @Test
    void convertsSingleLegacyOutputSourceToCanonicalSingularField() throws Exception {
        Path root = Files.createTempDirectory("legacy-output");
        Path file = root.resolve("recipe.yml");
        Files.writeString(file, "outputs:\n  - item_sources:\n      - minecraft:iron_ingot\n    amount: 1\n");
        LegacyTargetSpec spec = LegacyTargetSpec.replace(null, "outputs[]", "item_sources", "item_source");

        LegacyItemSourceRewriter.RunReport report = new LegacyItemSourceRewriter(root, List.of(spec), Logger.getAnonymousLogger()).run(true);

        assertTrue(report.applied());
        String converted = Files.readString(file);
        assertTrue(converted.contains("item_source: \"minecraft:iron_ingot\""));
        assertFalse(converted.contains("item_sources:"));
        assertTrue(Files.exists(root.resolve("recipe.yml.bak")));
    }

    @Test
    void blocksEmptyAndMultipleLegacyOutputSources() throws Exception {
        Path root = Files.createTempDirectory("legacy-output-block");
        Path empty = root.resolve("empty.yml");
        Path multiple = root.resolve("multiple.yml");
        Files.writeString(empty, "outputs:\n  - item_sources: []\n");
        Files.writeString(multiple, "outputs:\n  - item_sources:\n      - minecraft:iron_ingot\n      - minecraft:gold_ingot\n");
        LegacyTargetSpec spec = LegacyTargetSpec.replace(null, "outputs[]", "item_sources", "item_source");

        LegacyItemSourceRewriter.RunReport report = new LegacyItemSourceRewriter(root, List.of(spec), Logger.getAnonymousLogger()).run(true);

        assertFalse(report.applied());
        assertTrue(report.count(LegacyItemSourceRewriter.Status.UNCONVERTIBLE) >= 2);
        assertTrue(Files.readString(empty).contains("item_sources: []"));
        assertTrue(Files.readString(multiple).contains("item_sources:"));
        assertFalse(Files.exists(root.resolve("empty.yml.bak")));
        assertFalse(Files.exists(root.resolve("multiple.yml.bak")));
    }

    @Test
    void blocksCanonicalAndLegacyOutputTogether() throws Exception {
        Path root = Files.createTempDirectory("legacy-output-conflict");
        Path file = root.resolve("recipe.yml");
        Files.writeString(file, "outputs:\n  - item_sources:\n      - minecraft:iron_ingot\n    item_source: minecraft:gold_ingot\n");
        LegacyTargetSpec spec = LegacyTargetSpec.replace(null, "outputs[]", "item_sources", "item_source");

        LegacyItemSourceRewriter.RunReport report = new LegacyItemSourceRewriter(root, List.of(spec), Logger.getAnonymousLogger()).run(true);

        assertFalse(report.applied());
        assertTrue(report.count(LegacyItemSourceRewriter.Status.UNCONVERTIBLE) == 1);
        assertEquals("outputs:\n  - item_sources:\n      - minecraft:iron_ingot\n    item_source: minecraft:gold_ingot\n", Files.readString(file));
    }

    @Test
    void appliesValidFilesAndPreservesUnconvertibleFiles() throws Exception {
        Path root = Files.createTempDirectory("legacy-block");
        Path valid = root.resolve("valid.yml");
        Path invalid = root.resolve("invalid.yml");
        Files.writeString(valid, "input:\n  item_source:\n    - minecraft:iron_ingot\n");
        Files.writeString(invalid, "input:\n  item_source: minecraft:iron_ingot\n");
        LegacyTargetSpec spec = LegacyTargetSpec.replace(null, "input", "item_source", "item_sources");

        LegacyItemSourceRewriter.RunReport report = new LegacyItemSourceRewriter(root, List.of(spec), Logger.getAnonymousLogger()).run(true);

        assertTrue(report.applied());
        assertTrue(Files.readString(valid).contains("item_sources:"));
        assertFalse(Files.readString(valid).contains("item_source:"));
        assertTrue(Files.readString(invalid).contains("item_source: minecraft:iron_ingot"));
        assertTrue(Files.exists(root.resolve("valid.yml.bak")));
        assertTrue(report.files().stream().anyMatch(file -> file.status() == LegacyItemSourceRewriter.Status.UNCONVERTIBLE));
    }
}
