package emaki.jiuwu.craft.strengthen.log;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public enum StrengthenLogTag implements LogTagType {

    AFFIX("affix"),
    ATTEMPT("attempt"),
    LIFECYCLE("lifecycle"),
    MASTERY("mastery");

    private final String key;

    StrengthenLogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
