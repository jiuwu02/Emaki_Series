package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Turns a {@link CompileDiagnostic} into the sentence a server owner can act on.
 *
 * <p>Every diagnostic carries a stable {@code reasonKey} plus the machine-readable detail behind it. Until
 * this class existed the display sites printed that key verbatim, so an author saw
 * {@code action.validate.positional_not_allowed} and had to read CoreLib's source to learn what it meant.
 * The reason keys stay untouched — they are the stable contract other code branches on — and only the
 * presentation changes.</p>
 *
 * <h2>Why a resolver function instead of the message service</h2>
 *
 * <p>The {@code compile} package deliberately has no Bukkit dependency so each failure path stays testable
 * in isolation. Taking a resolver keeps that property: the caller supplies lookup, this class only decides
 * what to look up and how to assemble the parts.</p>
 *
 * <h2>Assembly order</h2>
 *
 * <p>Main sentence, then a location suffix, then a candidate suffix. Each part is a separate language key
 * so a translation can reorder or drop the trailing detail without the renderer concatenating localized
 * fragments itself.</p>
 */
public final class DiagnosticRenderer {

    /** Language key used when {@code reasonKey} itself has no translation. */
    public static final String UNKNOWN_REASON_KEY = "action.diagnostic.unknown_reason";

    /** Language key appended when the diagnostic knows its column but not its file. */
    public static final String AT_COLUMN_KEY = "action.diagnostic.at_column";

    /** Language key appended when the diagnostic was anchored to a config file. */
    public static final String AT_FILE_KEY = "action.diagnostic.at_file";

    /** Language key appended when the diagnostic carries suggested valid values. */
    public static final String CANDIDATES_KEY = "action.diagnostic.candidates";

    /** Language key used to report how many further problems the same line has. */
    public static final String MORE_PROBLEMS_KEY = "action.diagnostic.more_problems";

    /**
     * How many candidates are shown before the list is truncated.
     *
     * <p>An unknown stage offers every registered id, which runs to dozens. Printing all of them pushes the
     * actual problem off screen, which is why {@link CompileDiagnostic#toString()} leaves candidates out
     * entirely.</p>
     */
    private static final int MAX_CANDIDATES = 8;

    private final Resolver resolver;

    /**
     * Creates a renderer.
     *
     * @param resolver looks a language key up with placeholder replacements, returning the supplied
     *        fallback when that key has no translation
     */
    public DiagnosticRenderer(@NotNull Resolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Renders one diagnostic.
     *
     * @param diagnostic the diagnostic, may be {@code null}
     * @return a readable sentence, never {@code null}
     */
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

    /**
     * Renders a runtime reason key and its arguments.
     *
     * <p>{@code PipelineOutcome} reports failures the same way the compiler does, so the run path reuses
     * this renderer rather than growing a second lookup convention.</p>
     *
     * @param reasonKey the language key
     * @param args placeholder arguments
     * @return a readable sentence, never {@code null}
     */
    public @NotNull String render(@Nullable String reasonKey, @Nullable Map<String, ?> args) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        if (args != null) {
            placeholders.putAll(args);
        }
        return main(reasonKey, placeholders);
    }

    /**
     * Renders the first diagnostic and says how many others the same line has.
     *
     * <p>Only the first is spelled out: the validator reports every problem it finds, and the later ones are
     * usually consequences of the first.</p>
     *
     * @param diagnostics all diagnostics for one line
     * @return a readable sentence, never {@code null}
     */
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

    /**
     * Resolves the main sentence, falling back to a wrapper that still names the raw key.
     *
     * <p>A key with no translation must not disappear: the raw key is the only thing that lets an author
     * report the gap, so the fallback carries it rather than printing an empty line.</p>
     */
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

    /**
     * Builds the location suffix.
     *
     * <p>Prefers the file form when the diagnostic was anchored to a config entry, because that is what an
     * author needs in order to open the right file. A hand-typed line has no file, so the column alone is
     * the whole answer there.</p>
     */
    private String location(CompileDiagnostic diagnostic, Map<String, Object> placeholders) {
        if (!diagnostic.file().isEmpty()) {
            return resolve(AT_FILE_KEY, placeholders, "");
        }
        if (diagnostic.column() > 0) {
            return resolve(AT_COLUMN_KEY, placeholders, "");
        }
        return "";
    }

    /**
     * Collects every placeholder a message may reference.
     *
     * <p>The intrinsic fields go in first so a stage-supplied detail entry of the same name wins. That order
     * matters for {@code value}: a detail entry naming the offending argument is more specific than the raw
     * token text.</p>
     */
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

    /** Joins candidates, truncating the long lists an unknown stage produces. */
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

    /**
     * Looks one language key up.
     *
     * <p>Implemented by the message service, which already owns language loading. Declared here as a
     * function so this package keeps no Bukkit dependency.</p>
     */
    @FunctionalInterface
    public interface Resolver {

        /**
         * Resolves a key.
         *
         * @param key the language key
         * @param replacements placeholder replacements
         * @param fallback returned when the key has no translation
         * @return the resolved text, or {@code fallback} when the key is missing
         */
        @Nullable String resolve(@NotNull String key, @Nullable Map<String, ?> replacements, @Nullable String fallback);
    }
}
