package emaki.jiuwu.craft.corelib.action.v2.compile;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One compile-time problem, located precisely enough to fix without guessing.
 *
 * @param reasonKey stable language key
 * @param file config file path, empty when compiling a bare string
 * @param keyPath YAML key path within that file
 * @param line one-based line number within the action list
 * @param column one-based column within the line, {@code 0} when not column-specific
 * @param token the offending token text
 * @param detail extra machine-readable detail, for example the pairing column of an unclosed bracket
 * @param candidates suggested valid values, for example known stage ids
 */
public record CompileDiagnostic(@NotNull String reasonKey,
        @NotNull String file,
        @NotNull String keyPath,
        int line,
        int column,
        @NotNull String token,
        @NotNull Map<String, Object> detail,
        @NotNull List<String> candidates) {

    public CompileDiagnostic {
        reasonKey = reasonKey == null ? "" : reasonKey;
        file = file == null ? "" : file;
        keyPath = keyPath == null ? "" : keyPath;
        token = token == null ? "" : token;
        detail = detail == null ? Map.of() : Map.copyOf(detail);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /**
     * Creates a diagnostic anchored at one token.
     *
     * @param reasonKey stable language key
     * @param token the offending token
     * @return the diagnostic
     */
    public static @NotNull CompileDiagnostic at(@NotNull String reasonKey, @Nullable PipelineToken token) {
        return new CompileDiagnostic(reasonKey, "", "", 0,
                token == null ? 0 : token.column(),
                token == null ? "" : token.text(),
                Map.of(), List.of());
    }

    /**
     * Creates a diagnostic anchored at one token, carrying extra detail.
     *
     * @param reasonKey stable language key
     * @param token the offending token
     * @param detail machine-readable detail
     * @return the diagnostic
     */
    public static @NotNull CompileDiagnostic at(@NotNull String reasonKey,
            @Nullable PipelineToken token,
            @Nullable Map<String, Object> detail) {
        return new CompileDiagnostic(reasonKey, "", "", 0,
                token == null ? 0 : token.column(),
                token == null ? "" : token.text(),
                detail == null ? Map.of() : detail, List.of());
    }

    /**
     * Creates a diagnostic listing valid alternatives.
     *
     * @param reasonKey stable language key
     * @param token the offending token
     * @param candidates valid values the author could have meant
     * @return the diagnostic
     */
    public static @NotNull CompileDiagnostic suggesting(@NotNull String reasonKey,
            @Nullable PipelineToken token,
            @Nullable List<String> candidates) {
        return new CompileDiagnostic(reasonKey, "", "", 0,
                token == null ? 0 : token.column(),
                token == null ? "" : token.text(),
                Map.of(), candidates == null ? List.of() : candidates);
    }

    /**
     * Returns a copy anchored at a config location.
     *
     * @param newFile config file path
     * @param newKeyPath YAML key path
     * @param newLine one-based line number
     * @return the located copy
     */
    public @NotNull CompileDiagnostic locatedAt(@Nullable String newFile, @Nullable String newKeyPath, int newLine) {
        return new CompileDiagnostic(reasonKey, newFile, newKeyPath, newLine, column, token, detail, candidates);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(reasonKey);
        if (!file.isEmpty()) {
            builder.append(" [").append(file);
            if (!keyPath.isEmpty()) {
                builder.append(':').append(keyPath);
            }
            if (line > 0) {
                builder.append('#').append(line);
            }
            builder.append(']');
        }
        if (column > 0) {
            builder.append(" col=").append(column);
        }
        if (!token.isEmpty()) {
            builder.append(" token='").append(token).append('\'');
        }
        if (!detail.isEmpty()) {
            builder.append(' ').append(detail);
        }
        if (!candidates.isEmpty()) {
            builder.append(" candidates=").append(candidates);
        }
        return builder.toString();
    }
}
