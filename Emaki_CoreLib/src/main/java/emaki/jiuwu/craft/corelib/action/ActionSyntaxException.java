package emaki.jiuwu.craft.corelib.action;

public final class ActionSyntaxException extends Exception {

    private final int lineNumber;
    private final String rawLine;

    public ActionSyntaxException(int lineNumber, String rawLine, String message) {
        super(message);
        this.lineNumber = lineNumber;
        this.rawLine = rawLine == null ? "" : rawLine;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public String rawLine() {
        return rawLine;
    }
}
