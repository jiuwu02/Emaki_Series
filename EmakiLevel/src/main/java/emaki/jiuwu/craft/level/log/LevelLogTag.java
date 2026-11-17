package emaki.jiuwu.craft.level.log;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public enum LevelLogTag implements LogTagType {

    LEADERBOARD("leaderboard");

    private final String key;

    LevelLogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
