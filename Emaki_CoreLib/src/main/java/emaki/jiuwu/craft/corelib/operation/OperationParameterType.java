package emaki.jiuwu.craft.corelib.operation;

import emaki.jiuwu.craft.corelib.text.Texts;

public enum OperationParameterType {
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
                case INTEGER -> {
                    Integer.parseInt(raw);
                    yield true;
                }
                case DOUBLE -> {
                    Double.parseDouble(raw);
                    yield true;
                }
                case BOOLEAN -> "true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw);
                case TIME -> OperationParsers.parseTicks(raw) >= 0L;
                case STRING -> true;
            };
        } catch (Exception ignored) {
            return false;
        }
    }
}
