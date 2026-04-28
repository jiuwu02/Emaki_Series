package emaki.jiuwu.craft.corelib.action;

import emaki.jiuwu.craft.corelib.text.Texts;

public enum ActionParameterType {
    STRING,
    INTEGER,
    DOUBLE,
    BOOLEAN,
    TIME;

    public boolean isValid(String raw) {
        if (this == STRING) {
            return true;
        }
        if (Texts.isBlank(raw)) {
            return false;
        }
        try {
            return switch (this) {
                case INTEGER ->
                    ActionParsers.parseIntNullable(raw) != null;
                case DOUBLE ->
                    ActionParsers.parseDoubleNullable(raw) != null;
                case BOOLEAN ->
                    ActionParsers.parseBoolean(raw) != null;
                case TIME ->
                    ActionParsers.parseTicks(raw) >= 0L;
                case STRING ->
                    true;
            };
        } catch (Exception _) {
            return false;
        }
    }
}
