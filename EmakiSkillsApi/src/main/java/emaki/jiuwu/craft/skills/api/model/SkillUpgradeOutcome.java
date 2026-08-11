package emaki.jiuwu.craft.skills.api.model;

import org.jetbrains.annotations.NotNull;

/** Result payload for an upgrade process that ran to completion. */
public record SkillUpgradeOutcome(
        @NotNull String skillId,
        int fromLevel,
        int toLevel,
        int maxLevel,
        double successRate,
        boolean successfulRoll,
        boolean levelChanged,
        boolean downgraded) {

    public SkillUpgradeOutcome {
        skillId = skillId == null ? "" : skillId;
        fromLevel = Math.max(0, fromLevel);
        toLevel = Math.max(0, toLevel);
        maxLevel = Math.max(1, maxLevel);
        successRate = Math.max(0D, Math.min(100D, successRate));
    }
}
