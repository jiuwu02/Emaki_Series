package emaki.jiuwu.craft.skills.api.model;

import org.jetbrains.annotations.NotNull;

/** Result payload for a successfully committed skill cast. */
public record SkillCastOutcome(@NotNull String skillId, @NotNull String triggerId) {

    public SkillCastOutcome {
        skillId = skillId == null ? "" : skillId;
        triggerId = triggerId == null ? "" : triggerId;
    }
}
