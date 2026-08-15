package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class DiagnosticRenderer {

    public static final String UNKNOWN_REASON_KEY = "action.diagnostic.unknown_reason";

    public static final String AT_COLUMN_KEY = "action.diagnostic.at_column";

    public static final String AT_FILE_KEY = "action.diagnostic.at_file";

    public static final String CANDIDATES_KEY = "action.diagnostic.candidates";

    public static final String MORE_PROBLEMS_KEY = "action.diagnostic.more_problems";

    private static final int MAX_CANDIDATES = 8;

    private final Resolver resolver;

    public DiagnosticRenderer(@NotNull Resolver resolver) {
        this.resolver = resolver;
    }

    public @NotNull String render(@Nullable CompileDiagnostic diagnostic) {
        if (diagnostic == null) {
            return resolve(UNKNOWN_REASON_KEY, Map.of("reason", ""), "");
        }
        Map<String, Object> placeholders = placeholders(diagnostic);
        StringBuilder rendered = new StringBuilder(main(diagnostic.reasonKey(), placeholders));
        String location = location(diagnostic, placeholders);
        if (!location.isEmpty()) {
            rendered.append(location);
        }
        if (!diagnostic.candidates().isEmpty()) {
            rendered.append(resolve(CANDIDATES_KEY, placeholders, ""));
        }
        return rendered.toString();
    }

    public @NotNull String render(@Nullable String reasonKey, @Nullable Map<String, ?> args) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        if (args != null) {
            placeholders.putAll(args);
        }
        return main(reasonKey, placeholders);
    }

    public @NotNull String renderFirst(@Nullable List<CompileDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return resolve(UNKNOWN_REASON_KEY, Map.of("reason", ""), "");
        }
        String rendered = render(diagnostics.get(0));
        int remaining = diagnostics.size() - 1;
        if (remaining <= 0) {
            return rendered;
        }
        return rendered + resolve(MORE_PROBLEMS_KEY, Map.of("count", remaining), "");
    }

    private String main(String reasonKey, Map<String, Object> placeholders) {
        if (Texts.isBlank(reasonKey)) {
            return resolve(UNKNOWN_REASON_KEY, Map.of("reason", ""), "");
        }
        String resolved = resolver.resolve(reasonKey, placeholders, "");
        if (!Texts.isBlank(resolved)) {
            return resolved;
        }
        Map<String, Object> withReason = new LinkedHashMap<>(placeholders);
        withReason.put("reason", reasonKey);
        return resolve(UNKNOWN_REASON_KEY, withReason, reasonKey);
    }

    private String location(CompileDiagnostic diagnostic, Map<String, Object> placeholders) {
        if (!diagnostic.file().isEmpty()) {
            return resolve(AT_FILE_KEY, placeholders, "");
        }
        if (diagnostic.column() > 0) {
            return resolve(AT_COLUMN_KEY, placeholders, "");
        }
        return "";
    }

    private Map<String, Object> placeholders(CompileDiagnostic diagnostic) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("token", diagnostic.token());
        placeholders.put("column", diagnostic.column());
        placeholders.put("line", diagnostic.line());
        placeholders.put("file", diagnostic.file());
        placeholders.put("key_path", diagnostic.keyPath());
        placeholders.put("candidates", candidates(diagnostic.candidates()));
        placeholders.putAll(diagnostic.detail());
        return placeholders;
    }

    private String candidates(List<String> candidates) {
        if (candidates.isEmpty()) {
            return "";
        }
        List<String> shown = new ArrayList<>();
        for (String candidate : candidates) {
            if (shown.size() >= MAX_CANDIDATES) {
                break;
            }
            if (!Texts.isBlank(candidate)) {
                shown.add(candidate);
            }
        }
        String joined = String.join(", ", shown);
        int hidden = candidates.size() - shown.size();
        return hidden > 0 ? joined + " ... (+" + hidden + ")" : joined;
    }

    private String resolve(String key, Map<String, ?> placeholders, String fallback) {
        String resolved = resolver.resolve(key, placeholders, fallback);
        return resolved == null ? fallback : resolved;
    }

    @FunctionalInterface
    public interface Resolver {

        @Nullable String resolve(@NotNull String key, @Nullable Map<String, ?> replacements, @Nullable String fallback);
    }
}
