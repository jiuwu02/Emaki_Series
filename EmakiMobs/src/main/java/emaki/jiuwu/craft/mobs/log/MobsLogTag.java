package emaki.jiuwu.craft.mobs.log;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public enum MobsLogTag implements LogTagType {

    LOOT("loot");

    private final String key;

    MobsLogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
