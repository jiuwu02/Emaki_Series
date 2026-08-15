package emaki.jiuwu.craft.corelib.action.legacy;

final class LegacySyntaxException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int lineNumber;
    private final String rawLine;

    LegacySyntaxException(int lineNumber, String rawLine, String message) {
        super(message);
        this.lineNumber = lineNumber;
        this.rawLine = rawLine == null ? "" : rawLine;
    }

    int lineNumber() {
        return lineNumber;
    }

    String rawLine() {
        return rawLine;
    }
}
