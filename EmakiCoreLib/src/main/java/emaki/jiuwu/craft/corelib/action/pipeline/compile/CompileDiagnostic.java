package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    public static @NotNull CompileDiagnostic at(@NotNull String reasonKey, @Nullable PipelineToken token) {
        return new CompileDiagnostic(reasonKey, "", "", 0,
                token == null ? 0 : token.column(),
                token == null ? "" : token.text(),
                Map.of(), List.of());
    }

    public static @NotNull CompileDiagnostic at(@NotNull String reasonKey,
            @Nullable PipelineToken token,
            @Nullable Map<String, Object> detail) {
        return new CompileDiagnostic(reasonKey, "", "", 0,
                token == null ? 0 : token.column(),
                token == null ? "" : token.text(),
                detail == null ? Map.of() : detail, List.of());
    }

    public static @NotNull CompileDiagnostic suggesting(@NotNull String reasonKey,
            @Nullable PipelineToken token,
            @Nullable List<String> candidates) {
        return new CompileDiagnostic(reasonKey, "", "", 0,
                token == null ? 0 : token.column(),
                token == null ? "" : token.text(),
                Map.of(), candidates == null ? List.of() : candidates);
    }

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
        return builder.toString();
    }
}
