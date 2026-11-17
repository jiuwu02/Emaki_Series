package emaki.jiuwu.craft.kits.log;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public enum KitsLogTag implements LogTagType {

    KITS("kits"),
    LIMIT("limit");

    private final String key;

    KitsLogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
