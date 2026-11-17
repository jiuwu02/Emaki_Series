package emaki.jiuwu.craft.item.log;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public enum ItemLogTag implements LogTagType {

    EFFECT("effect"),
    REPAIR("repair"),
    SET("set");

    private final String key;

    ItemLogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
