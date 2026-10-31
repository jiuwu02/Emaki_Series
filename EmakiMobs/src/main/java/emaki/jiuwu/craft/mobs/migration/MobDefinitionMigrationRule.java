package emaki.jiuwu.craft.mobs.migration;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyContentMigrator;
import emaki.jiuwu.craft.corelib.legacy.LegacyEntryMover;

/**
 * 生物定义旧键自动迁移规则.
 *
 * <p>把根级的 {@code attributes} 改名为 {@code ea_attributes}, {@code skills} 改名为
 * {@code actions}. 两处只换键名, 内容原样保留, 注释与缩进不动.
 */
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
