package emaki.jiuwu.craft.item.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyContentMigrator;
import emaki.jiuwu.craft.corelib.legacy.LegacyEntryMover;

/**
 * 物品定义旧写法自动迁移规则.
 *
 * <p>把根级的效果简写搬进 {@code effects} 列表, 把旧的基础物品字段搬进 {@code item}, 让旧版本
 * 写下的物品文件在启动时自动升级到现行格式. 全部改写都在文本层完成, 注释与缩进风格保持不变.
 *
 * <p>只搬运能无损转换的写法: 效果简写, 技能触发器, 基础材质与显示组件. 根级 {@code amount}
 * 不再由定义承担数量, 这里保持原样留给加载器告警, 避免凭空丢掉服主写下的数值.
 */
public final class ItemDefinitionMigrationRule implements LegacyContentMigrator.Rule {

    /** 根级效果简写 → effects 条目. */
    private static final List<LegacyEntryMover.Spec> ROOT_EFFECTS = List.of(
            new LegacyEntryMover.Spec("variables", "variables", "variables"),
            new LegacyEntryMover.Spec("ea_attributes", "ea_attribute", "ea_attributes"),
            new LegacyEntryMover.Spec("es_skills", "es_skill", "es_skills"),
            new LegacyEntryMover.Spec("accessory_slots", "accessory_slot", "accessory_slots"),
            new LegacyEntryMover.Spec("name_actions", "name_action", "name_actions"),
            new LegacyEntryMover.Spec("lore_actions", "lore_action", "lore_actions")
    );

    /** 根级技能触发器简写: 先合成 es_skill 条目, 再挂到已有的 es_skills 条目里. */
    private static final List<LegacyEntryMover.Spec> ROOT_TRIGGERS = List.of(
            new LegacyEntryMover.Spec("es_skill_triggers", "es_skill", "es_skill_triggers"),
            new LegacyEntryMover.Spec("skill_triggers", "es_skill", "es_skill_triggers")
    );

    /** 根级显示字段 → item.components 下的组件. */
    private static final List<String[]> COMPONENT_FIELDS = List.of(
            new String[] {"display_name", "custom_name"},
            new String[] {"item_name", "item_name"},
            new String[] {"lore", "lore"}
    );

    @Override
    public @NotNull LegacyContentMigrator.Outcome apply(@NotNull List<String> lines) {
        List<String> changes = new ArrayList<>();
        List<String> working = lines;

        LegacyContentMigrator.Outcome outcome = LegacyEntryMover.moveRootKeys(working, ROOT_EFFECTS);
        working = outcome.lines();
        changes.addAll(outcome.changes());

        outcome = LegacyEntryMover.moveRootKeys(working, ROOT_TRIGGERS);
        working = outcome.lines();
        changes.addAll(outcome.changes());

        List<String> renamed = LegacyEntryMover.renameWithinEntries(
                working, "es_skill", "skill_triggers", "es_skill_triggers");
        if (renamed != working) {
            working = renamed;
            changes.add("effects.skill_triggers");
        }

        // material / source 旧写法 → item.source, 旧值按 CoreLib 物品源简写补 minecraft- 前缀.
        outcome = LegacyEntryMover.hoistToParent(working, "material", "item", "source",
                ItemDefinitionMigrationRule::toItemSource);
        working = outcome.lines();
        changes.addAll(outcome.changes());

        outcome = LegacyEntryMover.hoistToParent(working, "source", "item", "source",
                ItemDefinitionMigrationRule::toItemSource);
        working = outcome.lines();
        changes.addAll(outcome.changes());

        for (String[] field : COMPONENT_FIELDS) {
            outcome = LegacyEntryMover.moveIntoComponents(working, field[0], "item", field[1]);
            working = outcome.lines();
            changes.addAll(outcome.changes());
        }

        if (changes.isEmpty()) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        return new LegacyContentMigrator.Outcome(working, changes);
    }

    /** 旧 {@code material} 值是原版材质名, 新写法要求 CoreLib 的物品源简写. */
    private static String toItemSource(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.contains("-")) {
            return normalized;
        }
        return "minecraft-" + normalized;
    }
}
