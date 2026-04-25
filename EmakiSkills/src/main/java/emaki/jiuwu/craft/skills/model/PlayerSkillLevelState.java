package emaki.jiuwu.craft.skills.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class PlayerSkillLevelState {

    private final String skillId;
    private int level;

    public PlayerSkillLevelState(String skillId, int level) {
        this.skillId = Texts.normalizeId(skillId);
        this.level = Math.max(1, level);
    }

    public String skillId() {
        return skillId;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }
}
