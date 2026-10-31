package emaki.jiuwu.craft.item.migration;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyContentMigrator;
import emaki.jiuwu.craft.corelib.legacy.LegacyEntryMover;
import emaki.jiuwu.craft.corelib.legacy.YamlBlockLocator;

/**
 * 套装定义旧写法自动迁移规则.
 *
 * <p>套装的效果写在 {@code thresholds.<件数>} 下, 旧版本在那里直接放 {@code ea_attributes},
 * {@code es_skills}, {@code name_actions} 等简写. 本规则按缩进定位每个阈值段, 再把其中的简写
 * 搬进该阈值自己的 {@code effects} 列表.
 */
public final class ItemSetMigrationRule implements LegacyContentMigrator.Rule {

    private static final List<LegacyEntryMover.Spec> THRESHOLD_EFFECTS = List.of(
            new LegacyEntryMover.Spec("ea_attributes", "ea_attribute", "ea_attributes"),
            new LegacyEntryMover.Spec("es_skills", "es_skill", "es_skills"),
            new LegacyEntryMover.Spec("name_actions", "name_action", "name_actions"),
            new LegacyEntryMover.Spec("lore_actions", "lore_action", "lore_actions"),
            new LegacyEntryMover.Spec("name_action", "name_action", "name_actions"),
            new LegacyEntryMover.Spec("lore_action", "lore_action", "lore_actions")
    );

    @Override
    public @NotNull LegacyContentMigrator.Outcome apply(@NotNull List<String> lines) {
        int[] thresholds = LegacyEntryMover.findRootBlock(lines, "thresholds");
        if (thresholds == null) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        int thresholdIndent = YamlBlockLocator.indentOf(lines.get(thresholds[0])) + 2;
        int keyIndent = thresholdIndent + 2;
        // 逐段改写会改变后续段的行号, 因此先取全部阈值段, 再从后往前改.
        List<int[]> children = LegacyEntryMover.childBlocks(lines, thresholds[0] + 1, thresholds[1], thresholdIndent);
        List<String> working = lines;
        List<String> changes = new ArrayList<>();
        for (int index = children.size() - 1; index >= 0; index--) {
            int[] child = children.get(index);
            LegacyContentMigrator.Outcome outcome = LegacyEntryMover.moveKeysInRange(
                    working, child[0], child[1], keyIndent, THRESHOLD_EFFECTS);
            if (outcome.changed()) {
                working = outcome.lines();
                changes.addAll(outcome.changes());
            }
        }
        if (changes.isEmpty()) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        return new LegacyContentMigrator.Outcome(working, changes);
    }
}
