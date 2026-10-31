package emaki.jiuwu.craft.mobs.migration;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyContentMigrator;
import emaki.jiuwu.craft.corelib.legacy.LegacyEntryMover;

public final class MobDefinitionMigrationRule implements LegacyContentMigrator.Rule {

    private static final List<LegacyEntryMover.Rename> RENAMES = List.of(
            new LegacyEntryMover.Rename("attributes", "ea_attributes"),
            new LegacyEntryMover.Rename("skills", "actions")
    );

    @Override
    public @NotNull LegacyContentMigrator.Outcome apply(@NotNull List<String> lines) {
        return LegacyEntryMover.renameKeys(lines, 0, lines.size(), 0, RENAMES);
    }
}
