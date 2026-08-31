package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import org.jetbrains.annotations.NotNull;

public record PipelineToken(@NotNull Kind kind, @NotNull String text, int column, boolean quoted, int keySplit) {

    public PipelineToken {
        kind = kind == null ? Kind.WORD : kind;
        text = text == null ? "" : text;
    }

    public PipelineToken(@NotNull Kind kind, @NotNull String text, int column, boolean quoted) {
        this(kind, text, column, quoted, -1);
    }

    public boolean isKeyValue() {
        return keySplit > 0;
    }

    public @NotNull String key() {
        return keySplit > 0 ? text.substring(0, keySplit) : "";
    }

    public @NotNull String value() {
        return keySplit > 0 ? text.substring(keySplit + 1) : text;
    }

    public enum Kind {

        WORD,

        PIPE,

        BRACKET_OPEN,

        BRACKET_CLOSE
    }

    public boolean isPipe() {
        return kind == Kind.PIPE;
    }

    public boolean isWord() {
        return kind == Kind.WORD;
    }

    @Override
    public String toString() {
        return kind + "@" + column + "('" + text + "')";
    }
}
