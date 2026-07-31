package emaki.jiuwu.craft.corelib.action.v2.compile;

import org.jetbrains.annotations.NotNull;

/**
 * One lexed token, carrying the column it started at.
 *
 * <p>The column is what makes bracket-pairing diagnostics usable. v1's {@code ActionSyntaxException}
 * only had a line number, which was tolerable for short action lines but not for a pipeline that can
 * nest branches.</p>
 *
 * @param kind token role
 * @param text token text, with quotes stripped and escapes resolved
 * @param column one-based column where the token starts
 * @param quoted whether any part of the text came from a quoted value
 * @param keySplit index within {@code text} of the first {@code =} that appeared outside quotes, or
 *        {@code -1} when there is none. This is what makes {@code key="a=b"} split at the first
 *        {@code =} while leaving the one inside the quotes alone.
 */
public record PipelineToken(@NotNull Kind kind, @NotNull String text, int column, boolean quoted, int keySplit) {

    public PipelineToken {
        kind = kind == null ? Kind.WORD : kind;
        text = text == null ? "" : text;
    }

    /**
     * Creates a token with no key/value split.
     *
     * @param kind token role
     * @param text token text
     * @param column one-based start column
     * @param quoted whether the text came from quotes
     */
    public PipelineToken(@NotNull Kind kind, @NotNull String text, int column, boolean quoted) {
        this(kind, text, column, quoted, -1);
    }

    /** {@return whether this token is a {@code key=value} pair} */
    public boolean isKeyValue() {
        return keySplit > 0;
    }

    /** {@return the key part, or an empty string when this is not a pair} */
    public @NotNull String key() {
        return keySplit > 0 ? text.substring(0, keySplit) : "";
    }

    /** {@return the value part, or the whole text when this is not a pair} */
    public @NotNull String value() {
        return keySplit > 0 ? text.substring(keySplit + 1) : text;
    }

    /** Token roles the lexer distinguishes. */
    public enum Kind {

        /** A bare word or {@code key=value} pair. */
        WORD,

        /** The {@code |} stage separator. */
        PIPE,

        /** The {@code [} branch body opener. */
        BRACKET_OPEN,

        /** The {@code ]} branch body closer. */
        BRACKET_CLOSE
    }

    /** {@return whether this token is the pipe separator} */
    public boolean isPipe() {
        return kind == Kind.PIPE;
    }

    /** {@return whether this token is a word} */
    public boolean isWord() {
        return kind == Kind.WORD;
    }

    @Override
    public String toString() {
        return kind + "@" + column + "('" + text + "')";
    }
}
