package emaki.jiuwu.craft.skills.api;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

/**
 * A skill definition supplied by a third-party plugin instead of a YAML file.
 *
 * <p>Register an implementation through
 * {@link SkillExtensions#registerSkillDefinition(org.bukkit.plugin.Plugin, ExternalSkillDefinition)}. A registered
 * definition participates exactly like a YAML skill loaded from {@code skills/}: it is resolved by id during
 * casting, slot binding, level and upgrade queries, it feeds the skill-source collectors, and it is what
 * {@link SkillCatalog} reports.
 *
 * <h2>Overriding</h2>
 * A registered definition wins over a YAML skill that declares the same id. Registering again under the same
 * owner and id replaces the previous definition for that owner.
 *
 * <h2>Snapshot semantics</h2>
 * EmakiSkills reads the accessors each time it needs the definition and keeps the mapped result until the
 * registration changes, so a handler must expose stable, side-effect-free values. Build the definition once and
 * return the same values on every call.
 *
 * <h2>Behaviour</h2>
 * A code-defined skill is backed by CoreLib action lines: provide {@link #scriptLines()} per phase, and the
 * skill runs through the same pipeline a YAML {@code script.actions} block uses. Built-in stages and any stage
 * registered through EmakiCoreLib are usable in those lines. If {@link #scriptLines()} is empty but
 * {@link #mythicSkill()} names a MythicMobs skill, the skill is cast through MythicMobs instead.
 *
 * <p>Not every field of a YAML skill is exposed here. Upgrade tables, cron schedules, parameter declarations,
 * resource costs and the condition DSL are intentionally omitted, so a code-defined skill has no upgrade path
 * and no declarative cost or condition model; implement those with your own stages if needed.
 */
public interface ExternalSkillDefinition {

    /**
     * Canonical skill id.
     *
     * <p>Normalized the same way YAML skill ids are: trimmed, lower-cased with {@code Locale.ROOT}, with spaces
     * replaced by underscores. A blank id makes the registration inactive.
     *
     * @return the skill id
     */
    @NotNull String id();

    /** {@return the display name; falls back to {@link #id()} when blank} */
    default @NotNull String displayName() {
        return id();
    }

    /** {@return the description lines shown in skill UIs; empty when unset} */
    default @NotNull List<String> description() {
        return List.of();
    }

    /** {@return the material name used as the skill icon; empty when unset} */
    default @NotNull String iconMaterial() {
        return "";
    }

    /**
     * {@return how the skill is triggered, {@code ACTIVE} or {@code PASSIVE}}
     *
     * <p>Case-insensitive; any unrecognized value behaves as {@code ACTIVE}.
     */
    default @NotNull String activationType() {
        return "ACTIVE";
    }

    /** {@return the passive trigger ids this skill reacts to; ignored when active} */
    default @NotNull List<String> passiveTriggers() {
        return List.of();
    }

    /** {@return the cast cooldown in ticks; values below zero are clamped to zero} */
    default long cooldownTicks() {
        return 0L;
    }

    /** {@return the shared cooldown this skill imposes in ticks; values below zero are clamped to zero} */
    default long globalCooldownTicks() {
        return 0L;
    }

    /**
     * {@return CoreLib action lines per script phase, keyed by phase name}
     *
     * <p>Recognized phase keys are {@code cast}, {@code hit}, {@code miss} and {@code fail}, case-insensitive;
     * other keys are ignored. Each line uses the same syntax as a YAML skill's {@code script.actions} block. An
     * empty map disables the native pipeline for this skill.
     */
    default @NotNull Map<String, List<String>> scriptLines() {
        return Map.of();
    }

    /**
     * {@return gate conditions per script phase, keyed by phase name}
     *
     * <p>Uses the same keys and syntax as {@link #scriptLines()}; a phase runs only when its conditions pass.
     */
    default @NotNull Map<String, List<String>> scriptConditions() {
        return Map.of();
    }

    /** {@return the tags used for filtering and equip limits; empty when unset} */
    default @NotNull List<String> tags() {
        return List.of();
    }

    /** {@return the lore aliases that unlock this skill from item lore; empty when unset} */
    default @NotNull List<String> loreAliases() {
        return List.of();
    }

    /** {@return the persistent-data skill id used for equipment matching; empty to use {@link #id()}} */
    default @NotNull String pdcSkillId() {
        return "";
    }

    /** {@return the UI category this skill belongs to; {@code default} when blank} */
    default @NotNull String uiCategory() {
        return "default";
    }

    /** {@return the display order inside a category; lower appears first} */
    default int sortOrder() {
        return 0;
    }

    /** {@return whether this skill appears in skill-slot UIs} */
    default boolean showInSlots() {
        return true;
    }

    /**
     * {@return whether this skill is currently enabled}
     *
     * <p>A disabled definition stays registered but is never reported as unlocked and cannot be cast.
     */
    default boolean enabled() {
        return true;
    }

    /**
     * {@return the MythicMobs skill name to cast}
     *
     * <p>Used only when {@link #scriptLines()} is empty; blank when the skill is purely CoreLib-backed.
     */
    default @NotNull String mythicSkill() {
        return "";
    }
}
