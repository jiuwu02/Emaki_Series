package emaki.jiuwu.craft.dungeon.log;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public enum DungeonLogTag implements LogTagType {

    CHALLENGE("challenge"),
    REWARDBOX("rewardbox"),
    STORAGE("storage");

    private final String key;

    DungeonLogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
