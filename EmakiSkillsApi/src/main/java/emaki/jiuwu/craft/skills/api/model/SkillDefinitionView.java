package emaki.jiuwu.craft.skills.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of a skill definition.
 *
 * <p>Many fields from EmakiSkills's internal {@code SkillDefinition} are not exposed: mythic skill
 * identifiers, script definitions, upgrade cost tables, condition DSL, and YAML lore aliases are all
 * configuration internals that server owners restructure freely. This view carries only what is stable
 * and useful to third parties.
 *
 * @param id             canonical lowercase skill id
 * @param displayName    display name; falls back to the id when unset
 * @param description    description lines; empty when unset
 * @param activationType how the skill is triggered
 * @param cooldownTicks  cast cooldown in ticks; {@code 0} when there is no cooldown
 * @param maxLevel       the maximum level this skill may reach; values below one default to one
 * @param enabled        whether this skill is currently enabled in configuration
 * @param tags           the tags attached to this skill for filtering
 * @param showInSlots    whether this skill appears in skill-slot UIs
 * @param uiCategory     the UI category this skill belongs to; empty when unset
 * @param sortOrder      display order inside a category; lower appears first
 */
public record SkillDefinitionView(@NotNull String id,
                                  @NotNull String displayName,
                                  @NotNull List<String> description,
                                  @NotNull String activationType,
                                  long cooldownTicks,
                                  int maxLevel,
                                  boolean enabled,
                                  @NotNull List<String> tags,
                                  boolean showInSlots,
                                  @NotNull String uiCategory,
                                  int sortOrder) {

    /**
     * Normalises every reference component so no accessor can return {@code null}.
     *
     * @param id             canonical lowercase skill id
     * @param displayName    display name
     * @param description    description lines
     * @param activationType trigger type
     * @param cooldownTicks  cast cooldown
     * @param maxLevel       maximum reachable level
     * @param enabled        whether enabled
     * @param tags           attached tags
     * @param showInSlots    whether shown in slot UIs
     * @param uiCategory     UI category
     * @param sortOrder      display order
     */
    public SkillDefinitionView {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        description = description == null ? List.of() : List.copyOf(description);
        activationType = activationType == null ? "" : activationType;
        cooldownTicks = Math.max(0L, cooldownTicks);
        maxLevel = Math.max(1, maxLevel);
        tags = tags == null ? List.of() : List.copyOf(tags);
        uiCategory = uiCategory == null ? "" : uiCategory;
    }

    /** {@return whether this is an active (manually triggered) skill} */
    public boolean active() {
        return "ACTIVE".equalsIgnoreCase(activationType);
    }

    /** {@return whether this is a passive skill} */
    public boolean passive() {
        return "PASSIVE".equalsIgnoreCase(activationType);
    }
}
