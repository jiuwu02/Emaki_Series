package emaki.jiuwu.craft.skills.model;

public record UnlockedSkillEntry(String skillId,
        String sourceId,
        SkillSourceType sourceType,
        String sourceSlot,
        String displayHint) {

    public UnlockedSkillEntry {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
        sourceId = sourceId == null ? "" : sourceId;
        sourceType = sourceType == null ? SkillSourceType.EQUIPMENT : sourceType;
    }
}
