package emaki.jiuwu.craft.item.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyContentMigrator;
import emaki.jiuwu.craft.corelib.legacy.LegacyEntryMover;

public final class ItemDefinitionMigrationRule implements LegacyContentMigrator.Rule {

    private static final List<LegacyEntryMover.Spec> ROOT_EFFECTS = List.of(
            new LegacyEntryMover.Spec("variables", "variables", "variables"),
            new LegacyEntryMover.Spec("ea_attributes", "ea_attribute", "ea_attributes"),
            new LegacyEntryMover.Spec("es_skills", "es_skill", "es_skills"),
            new LegacyEntryMover.Spec("accessory_slots", "accessory_slot", "accessory_slots"),
            new LegacyEntryMover.Spec("name_actions", "name_action", "name_actions"),
            new LegacyEntryMover.Spec("lore_actions", "lore_action", "lore_actions")
    );

    private static final List<LegacyEntryMover.Spec> ROOT_TRIGGERS = List.of(
            new LegacyEntryMover.Spec("es_skill_triggers", "es_skill", "es_skill_triggers"),
            new LegacyEntryMover.Spec("skill_triggers", "es_skill", "es_skill_triggers")
    );

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
