package emaki.jiuwu.craft.corelib.api.action.execution;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One stable diagnostic produced while compiling an action request.
 *
 * @param reasonKey language key describing the problem
 * @param source source text or source identifier associated with the problem
 * @param keyPath configuration key path associated with the problem
 * @param line one-based source line, or a non-positive value when unknown
 * @param column one-based source column, or a non-positive value when unknown
 * @param token token associated with the problem
 * @param details structured diagnostic arguments
 * @param candidates suggested replacement tokens or keys
 */
public record CoreActionCompileDiagnostic(@NotNull String reasonKey,
        @NotNull String source,
        @NotNull String keyPath,
        int line,
        int column,
        @NotNull String token,
        @NotNull Map<String, Object> details,
        @NotNull List<String> candidates) {

    public CoreActionCompileDiagnostic {
        reasonKey = reasonKey == null ? "" : reasonKey;
        source = source == null ? "" : source;
        keyPath = keyPath == null ? "" : keyPath;
        line = Math.max(0, line);
        column = Math.max(0, column);
        token = token == null ? "" : token;
        details = details == null ? Map.of() : Map.copyOf(details);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /**
     * Creates a diagnostic without structured details or replacement candidates.
     *
     * @param reasonKey language key describing the problem
     * @param source source text or identifier
     * @param keyPath configuration key path
     * @param line one-based source line, or a non-positive value when unknown
     * @param column one-based source column, or a non-positive value when unknown
     * @param token token associated with the problem
     */
    public CoreActionCompileDiagnostic(@Nullable String reasonKey,
            @Nullable String source,
            @Nullable String keyPath,
            int line,
            int column,
            @Nullable String token) {
        this(reasonKey, source, keyPath, line, column, token, Map.of(), List.of());
    }
}
