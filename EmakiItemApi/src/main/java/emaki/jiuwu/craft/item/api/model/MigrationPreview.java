package emaki.jiuwu.craft.item.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/** Read-only preview of an item-id migration. */
public record MigrationPreview(@NotNull String oldId,
                               @NotNull String newId,
                               boolean oldExists,
                               boolean newExists,
                               boolean aliasExists,
                               long aliasRevision,
                               @NotNull List<MigrationFileView> files,
                               int replacementCount) {

    public MigrationPreview {
        oldId = oldId == null ? "" : oldId;
        newId = newId == null ? "" : newId;
        aliasRevision = Math.max(0L, aliasRevision);
        files = files == null ? List.of() : List.copyOf(files);
        replacementCount = Math.max(0, replacementCount);
    }
}
