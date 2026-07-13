package emaki.jiuwu.craft.skills.model;

public record BoundSkillTrigger(String skillId,
        String triggerId,
        String sourceSlot) {

    public BoundSkillTrigger {
        skillId = skillId == null ? "" : skillId;
        triggerId = triggerId == null ? "" : triggerId;
        sourceSlot = sourceSlot == null ? "" : sourceSlot;
    }

    public boolean valid() {
        return !skillId.isBlank() && !triggerId.isBlank();
    }
}
