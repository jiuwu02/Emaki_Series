package emaki.jiuwu.craft.cooking.log;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public enum CookingLogTag implements LogTagType {

    COMPLETION("completion"),
    COOKING("cooking"),
    REWARD("reward");

    private final String key;

    CookingLogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
