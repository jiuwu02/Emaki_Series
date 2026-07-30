package emaki.jiuwu.craft.skills.api;

import org.jetbrains.annotations.NotNull;

/** One skill unlocked for a player by a registered external source. */
public record SkillSourceEntry(@NotNull String skillId,
                               @NotNull String sourceSlot,
                               @NotNull String displayHint) {

    public SkillSourceEntry {
        skillId = skillId == null ? "" : skillId.trim();
        sourceSlot = sourceSlot == null ? "" : sourceSlot;
        displayHint = displayHint == null ? "" : displayHint;
    }

    public SkillSourceEntry(@NotNull String skillId) {
        this(skillId, "", "");
    }
}
