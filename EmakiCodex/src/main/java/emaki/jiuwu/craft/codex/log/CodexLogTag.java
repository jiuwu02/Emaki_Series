package emaki.jiuwu.craft.codex.log;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public enum CodexLogTag implements LogTagType {

    ADVANCEMENT("advancement"),
    PACKET("packet"),
    PROVIDER("provider");

    private final String key;

    CodexLogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
