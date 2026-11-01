package emaki.jiuwu.craft.station.migration;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyContentMigrator;
import emaki.jiuwu.craft.corelib.legacy.LegacyEntryMover;
import emaki.jiuwu.craft.corelib.legacy.YamlBlockLocator;

public final class StationRecipeMigrationRule implements LegacyContentMigrator.Rule {

    private static final String MATERIALS = "materials";
    private static final String LEGACY_ITEM_SOURCE = "item_source";
    private static final String ITEM_SOURCES = "item_sources";
    private static final String LEGACY_INPUT_SOURCE = "input_source";

    @Override
    public @NotNull LegacyContentMigrator.Outcome apply(@NotNull List<String> lines) {
        List<String> working = new ArrayList<>(lines);
        List<String> changes = new ArrayList<>();
        migrateMaterials(working, changes);
        migrateInputSource(working, changes);
        if (changes.isEmpty()) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        return new LegacyContentMigrator.Outcome(working, changes);
    }

    private void migrateMaterials(List<String> working, List<String> changes) {
        int[] materials = LegacyEntryMover.findRootBlock(working, MATERIALS);
        if (materials == null) {
            return;
        }
        boolean renamed = false;
        for (int index = materials[1] - 1; index > materials[0]; index--) {
            if (convertEntry(working, index, LEGACY_ITEM_SOURCE)) {
                renamed = true;
            }
        }
        if (renamed) {
            changes.add(MATERIALS + "." + LEGACY_ITEM_SOURCE);
        }
    }

    private void migrateInputSource(List<String> working, List<String> changes) {
        int[] legacy = LegacyEntryMover.findRootBlock(working, LEGACY_INPUT_SOURCE);
        if (legacy == null || LegacyEntryMover.findRootBlock(working, ITEM_SOURCES) != null) {
            return;
        }
        if (convertEntry(working, legacy[0], LEGACY_INPUT_SOURCE)) {
            changes.add(LEGACY_INPUT_SOURCE);
        }
    }

    private static boolean convertEntry(List<String> working, int index, String legacyKey) {
        String line = working.get(index);
        int keyColumn = keyColumnOf(line);
        if (keyColumn < 0) {
            return false;
        }
        String rest = line.substring(keyColumn);
        String prefix = legacyKey + ":";
        if (!rest.startsWith(prefix)) {
            return false;
        }
        String value = rest.substring(prefix.length()).trim();
        if (value.isEmpty() || value.startsWith("#")) {
            return false;
        }
        String pad = " ".repeat(keyColumn);
        working.set(index, pad + ITEM_SOURCES + ":");
        working.add(index + 1, pad + "  - " + value);
        return true;
    }

    private static int keyColumnOf(String line) {
        int indent = YamlBlockLocator.indentOf(line);
        if (indent == Integer.MAX_VALUE) {
            return -1;
        }
        int column = indent;
        if (line.startsWith("- ", column)) {
            column += 2;
            while (column < line.length() && line.charAt(column) == ' ') {
                column++;
            }
        }
        return column;
    }
}
