package emaki.jiuwu.craft.item.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/** Files and aliases actually committed by an item-id migration. */
public record MigrationOutcome(@NotNull String oldId,
                               @NotNull String newId,
                               @NotNull List<MigrationFileView> changedFiles,
                               int replacementCount,
                               boolean aliasKept,
                               long aliasRevision) {

    public MigrationOutcome {
        oldId = oldId == null ? "" : oldId;
        newId = newId == null ? "" : newId;
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        replacementCount = Math.max(0, replacementCount);
        aliasRevision = Math.max(0L, aliasRevision);
    }
}
