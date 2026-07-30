package emaki.jiuwu.craft.item.api.model;

import org.jetbrains.annotations.NotNull;

/** One file inspected or changed by an item-id migration. */
public record MigrationFileView(@NotNull String moduleId,
                                @NotNull String path,
                                @NotNull String kind,
                                int replacements,
                                long revision) {

    public MigrationFileView {
        moduleId = moduleId == null ? "" : moduleId;
        path = path == null ? "" : path;
        kind = kind == null ? "" : kind;
        replacements = Math.max(0, replacements);
        revision = Math.max(0L, revision);
    }
}
