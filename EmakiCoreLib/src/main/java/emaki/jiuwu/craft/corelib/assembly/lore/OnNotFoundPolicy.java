package emaki.jiuwu.craft.corelib.assembly.lore;

import emaki.jiuwu.craft.corelib.text.Texts;

public enum OnNotFoundPolicy {
    SKIP("skip"),
    APPEND_TO_END("append_to_end"),
    ERROR("error");

    private final String key;

    OnNotFoundPolicy(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static OnNotFoundPolicy fromKey(String value) {
        String normalized = Texts.trim(Texts.lower(value));
        if (normalized.isEmpty() || "skip".equals(normalized)) {
            return SKIP;
        }
        if ("append_to_end".equals(normalized)) {
            return APPEND_TO_END;
        }
        if ("error".equals(normalized)) {
            return ERROR;
        }
        throw new SearchInsertValidationException(
                SearchInsertValidationException.Reason.INVALID_ON_NOT_FOUND,
                "Unsupported on_not_found policy: " + value
        );
    }
}
