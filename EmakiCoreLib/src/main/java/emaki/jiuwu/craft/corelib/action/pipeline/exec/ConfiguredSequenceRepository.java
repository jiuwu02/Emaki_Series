package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;

/**
 * Named sequences built from configuration.
 *
 * <p>A sequence is a list of pipeline lines addressable by name, which is what {@code run} and
 * {@code start_task} execute. Definitions come from configuration rather than code, so they are compiled
 * once when the action system reloads and held here in compiled form.</p>
 *
 * <p>Two-pass construction is required and not incidental: validating a {@code run} call needs the
 * catalog to already answer {@link #contains} and {@link #calls} for every name, including names that
 * appear later in the file. So names and raw bodies are collected first, and only then is each body
 * compiled against a catalog that can already see all of them.</p>
 */
public final class ConfiguredSequenceRepository implements SequenceRepository {

    /** Matches the {@code %var.name%} references a sequence body reads. */
    private static final Pattern VAR_REFERENCE = Pattern.compile("%var\\.([A-Za-z0-9_]+)%");

    private final Map<String, Entry> entries;

    private ConfiguredSequenceRepository(Map<String, Entry> entries) {
        this.entries = entries;
    }

    /**
     * Builds a repository from raw sequence definitions.
     *
     * @param definitions sequence name to its pipeline lines
     * @param compiler compiles one line against this catalog; returns {@code null} when it does not compile
     * @return the repository, holding only the sequences whose every line compiled
     */
    public static @NotNull ConfiguredSequenceRepository build(
            @Nullable Map<String, List<String>> definitions,
            @NotNull LineCompiler compiler) {
        Map<String, List<String>> raw = new LinkedHashMap<>();
        if (definitions != null) {
            definitions.forEach((name, lines) -> {
                if (name != null && !name.isBlank() && lines != null && !lines.isEmpty()) {
                    raw.put(key(name), List.copyOf(lines));
                }
            });
        }
        // Pass one: names, declared parameters and outgoing calls, all derived from text. This is what
        // lets validation of any single line see the whole catalog.
        Map<String, Entry> entries = new LinkedHashMap<>();
        raw.forEach((name, lines) -> entries.put(name,
                new Entry(name, lines, parametersOf(lines), callsOf(lines), null)));
        ConfiguredSequenceRepository catalog = new ConfiguredSequenceRepository(entries);
        // Pass two: compile each body now that the catalog answers for every name.
        raw.forEach((name, lines) -> {
            List<CompiledPipeline> compiled = new ArrayList<>(lines.size());
            for (String line : lines) {
                CompiledPipeline pipeline = compiler.compile(name, line, catalog);
                if (pipeline == null) {
                    compiled.clear();
                    break;
                }
                compiled.add(pipeline);
            }
            Entry existing = entries.get(name);
            entries.put(name, new Entry(existing.name(), existing.lines(), existing.parameters(),
                    existing.calls(), compiled.isEmpty() ? null : List.copyOf(compiled)));
        });
        return catalog;
    }

    /** {@return an empty repository} */
    public static @NotNull ConfiguredSequenceRepository empty() {
        return new ConfiguredSequenceRepository(Map.of());
    }

    /**
     * Reads the {@code %var.*%} names a body references.
     *
     * <p>Derived from the body rather than declared separately: a sequence that reads {@code %var.amount%}
     * requires {@code amount}, and making the author repeat that in a parameter list only creates a second
     * place to forget.</p>
     */
    private static Set<String> parametersOf(List<String> lines) {
        Set<String> parameters = new LinkedHashSet<>();
        for (String line : lines) {
            Matcher matcher = VAR_REFERENCE.matcher(line == null ? "" : line);
            while (matcher.find()) {
                parameters.add(matcher.group(1));
            }
        }
        return Set.copyOf(parameters);
    }

    /**
     * Reads the sequence names a body calls, for cycle detection.
     *
     * <p>Text-level extraction on purpose: cycle detection has to run before compilation, because
     * compiling a cyclic sequence is what the check exists to prevent.</p>
     */
    private static Set<String> callsOf(List<String> lines) {
        Set<String> calls = new LinkedHashSet<>();
        for (String line : lines) {
            String text = line == null ? "" : line;
            int at = 0;
            while (true) {
                int found = text.indexOf("run ", at);
                if (found < 0) {
                    break;
                }
                boolean atSegmentStart = found == 0 || isSegmentBoundary(text, found);
                at = found + 4;
                if (!atSegmentStart) {
                    continue;
                }
                int end = at;
                while (end < text.length() && !Character.isWhitespace(text.charAt(end))
                        && text.charAt(end) != '|') {
                    end++;
                }
                if (end > at) {
                    calls.add(key(text.substring(at, end)));
                }
            }
        }
        return Set.copyOf(calls);
    }

    /** Reports whether the text before this index is only a pipe and whitespace. */
    private static boolean isSegmentBoundary(String text, int index) {
        for (int cursor = index - 1; cursor >= 0; cursor--) {
            char ch = text.charAt(cursor);
            if (ch == '|' || ch == '[') {
                return true;
            }
            if (!Character.isWhitespace(ch)) {
                return false;
            }
        }
        return true;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public @Nullable CompiledPipeline find(@Nullable String name) {
        List<CompiledPipeline> body = bodyOf(name);
        return body == null || body.isEmpty() ? null : body.get(0);
    }

    /**
     * Resolves every compiled line of a sequence.
     *
     * @param name sequence name
     * @return the compiled lines, or {@code null} when the sequence is unknown or failed to compile
     */
    public @Nullable List<CompiledPipeline> bodyOf(@Nullable String name) {
        if (name == null) {
            return null;
        }
        Entry entry = entries.get(key(name));
        return entry == null ? null : entry.compiled();
    }

    /**
     * Reads a sequence's raw lines.
     *
     * @param name sequence name
     * @return the raw lines, empty when unknown
     */
    public @NotNull List<String> linesOf(@Nullable String name) {
        if (name == null) {
            return List.of();
        }
        Entry entry = entries.get(key(name));
        return entry == null ? List.of() : entry.lines();
    }

    @Override
    public boolean contains(@Nullable String name) {
        return name != null && entries.containsKey(key(name));
    }

    @Override
    public @NotNull Set<String> requiredParameters(@Nullable String name) {
        if (name == null) {
            return Set.of();
        }
        Entry entry = entries.get(key(name));
        return entry == null ? Set.of() : entry.parameters();
    }

    @Override
    public @NotNull Set<String> calls(@Nullable String name) {
        if (name == null) {
            return Set.of();
        }
        Entry entry = entries.get(key(name));
        return entry == null ? Set.of() : entry.calls();
    }

    @Override
    public @NotNull List<String> names() {
        return List.copyOf(entries.keySet());
    }

    /** {@return how many sequences are defined} */
    public int size() {
        return entries.size();
    }

    /** {@return the names whose body failed to compile} */
    public @NotNull List<String> failed() {
        List<String> names = new ArrayList<>();
        entries.forEach((name, entry) -> {
            if (entry.compiled() == null) {
                names.add(name);
            }
        });
        return List.copyOf(names);
    }

    /** Compiles one sequence line. */
    public interface LineCompiler {

        /**
         * Compiles a line belonging to a sequence.
         *
         * @param sequence the sequence being compiled, for diagnostics
         * @param line the pipeline line
         * @param catalog the catalog to validate {@code run} targets against
         * @return the compiled pipeline, or {@code null} when it did not compile
         */
        @Nullable
        CompiledPipeline compile(@NotNull String sequence,
                @NotNull String line,
                @NotNull SequenceRepository catalog);
    }

    /**
     * One sequence.
     *
     * @param name its name, lowercased
     * @param lines its raw pipeline lines
     * @param parameters the {@code %var.*%} names its body reads
     * @param calls the sequences it calls directly
     * @param compiled its compiled body, {@code null} when compilation failed
     */
    private record Entry(@NotNull String name,
            @NotNull List<String> lines,
            @NotNull Set<String> parameters,
            @NotNull Set<String> calls,
            @Nullable List<CompiledPipeline> compiled) {
    }

}
