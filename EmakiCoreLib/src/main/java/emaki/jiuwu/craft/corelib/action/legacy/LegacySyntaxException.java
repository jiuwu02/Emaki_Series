package emaki.jiuwu.craft.corelib.action.legacy;

/**
 * Thrown when an old-syntax action line cannot be parsed.
 *
 * <p>Moved here from the removed v1 action package so the one-shot converter can still read old syntax.
 * The converter treats this as "leave the line alone" rather than an error: a line the old parser rejects
 * was already broken before the migration, and rewriting it would be guessing at intent.</p>
 */
final class LegacySyntaxException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int lineNumber;
    private final String rawLine;

    /**
     * Creates the exception.
     *
     * @param lineNumber one-based line number
     * @param rawLine the text that failed to parse
     * @param message what was wrong
     */
    LegacySyntaxException(int lineNumber, String rawLine, String message) {
        super(message);
        this.lineNumber = lineNumber;
        this.rawLine = rawLine == null ? "" : rawLine;
    }

    /** {@return the one-based line number} */
    int lineNumber() {
        return lineNumber;
    }

    /** {@return the text that failed to parse} */
    String rawLine() {
        return rawLine;
    }
}
