package emaki.jiuwu.craft.attribute.log;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public enum AttributeLogTag implements LogTagType {

    COMBAT("combat");

    private final String key;

    AttributeLogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
