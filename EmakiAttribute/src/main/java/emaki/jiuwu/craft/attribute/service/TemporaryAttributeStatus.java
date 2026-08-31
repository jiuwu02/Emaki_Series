package emaki.jiuwu.craft.attribute.service;

import java.util.Locale;

public enum TemporaryAttributeStatus {

    APPLIED,

    REPLACED,

    STACKED,

    REMOVED,

    NOT_FOUND,

    NO_MATCH,

    INVALID_INPUT,

    UNKNOWN_ATTRIBUTE,

    WRONG_THREAD,

    CLOSED;

    public boolean successful() {
        return this == APPLIED || this == REPLACED || this == STACKED || this == REMOVED;
    }

    public boolean rejected() {
        return this == INVALID_INPUT || this == UNKNOWN_ATTRIBUTE || this == WRONG_THREAD || this == CLOSED;
    }

    public String reasonSuffix() {
        return name().toLowerCase(Locale.ROOT);
    }
}
