package emaki.jiuwu.craft.skills.migration;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyContentMigrator;
import emaki.jiuwu.craft.corelib.legacy.LegacyEntryMover;

/**
 * 技能定义旧写法自动迁移规则.
 *
 * <p>目前技能定义里只有一处旧写法: {@code tag_limits} 已改名为
 * {@code skill_tags.equip_limits}, 技能文件里出现旧键时把它整体搬进新的嵌套位置.
 */
public final class SkillDefinitionMigrationRule implements LegacyContentMigrator.Rule {

    private static final String LEGACY_KEY = "tag_limits";
    private static final String SKILL_TAGS = "skill_tags";
    private static final String EQUIP_LIMITS = "equip_limits";

    @Override
    public @NotNull LegacyContentMigrator.Outcome apply(@NotNull List<String> lines) {
        int[] legacy = LegacyEntryMover.findRootBlock(lines, LEGACY_KEY);
        if (legacy == null) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        List<String> working = new ArrayList<>(lines);
        List<String> changes = new ArrayList<>();
        changes.add(LEGACY_KEY);
        List<String> body = new ArrayList<>();
        for (int index = legacy[0] + 1; index < legacy[1]; index++) {
            String line = working.get(index);
            body.add(line.isBlank() ? "" : "  " + line);
        }
        while (!body.isEmpty() && body.getLast().isBlank()) {
            body.removeLast();
        }
        working.subList(legacy[0], legacy[1]).clear();
        int[] skillTags = LegacyEntryMover.findRootBlock(working, SKILL_TAGS);
        List<String> fragment = new ArrayList<>();
        fragment.add("  " + EQUIP_LIMITS + ":");
        fragment.addAll(body);
        if (skillTags == null) {
            fragment.add(0, SKILL_TAGS + ":");
            if (!working.isEmpty() && !working.getLast().isBlank()) {
                working.add("");
            }
            working.addAll(fragment);
        } else {
            working.addAll(skillTags[1], fragment);
        }
        return new LegacyContentMigrator.Outcome(working, changes);
    }
}
