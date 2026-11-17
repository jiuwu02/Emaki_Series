package emaki.jiuwu.craft.structure.log;

import emaki.jiuwu.craft.corelib.log.LogTagType;

public enum StructureLogTag implements LogTagType {

    CACHE("cache"),
    EXPORT("export"),
    FURNITURE("furniture"),
    REPLACE("replace"),
    ROLLBACK("rollback");

    private final String key;

    StructureLogTag(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
