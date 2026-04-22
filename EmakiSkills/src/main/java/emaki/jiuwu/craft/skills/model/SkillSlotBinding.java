package emaki.jiuwu.craft.skills.model;

public record SkillSlotBinding(int slotIndex,
        String skillId,
        String triggerId) {

    public SkillSlotBinding {
        slotIndex = Math.max(0, slotIndex);
    }

    public boolean isEmpty() {
        return skillId == null || skillId.isBlank();
    }
}
