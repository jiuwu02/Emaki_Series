package emaki.jiuwu.craft.skills.log;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public enum SkillsLogTag implements LogTagType {

    ACTIONBAR("actionbar"),
    CAST("cast"),
    UPGRADE("upgrade");

    private final String key;

    SkillsLogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
