package emaki.jiuwu.craft.forge.log;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public enum ForgeLogTag implements LogTagType {

    DELIVERY("delivery");

    private final String key;

    ForgeLogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
