package emaki.jiuwu.craft.corelib.assembly.lore;

public final class SearchInsertValidationException extends IllegalArgumentException {

    public enum Reason {
        INVALID_ACTION_NAME,
        INVALID_TARGET_PATTERN,
        INVALID_CONTENT,
        INVALID_ON_NOT_FOUND,
        INVALID_MATCH_INDEX,
        INVALID_REGEX,
        INVALID_SERIALIZED_CONFIG
    }

    private final Reason reason;

    public SearchInsertValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason == null ? Reason.INVALID_SERIALIZED_CONFIG : reason;
    }

    public Reason reason() {
        return reason;
    }
}
