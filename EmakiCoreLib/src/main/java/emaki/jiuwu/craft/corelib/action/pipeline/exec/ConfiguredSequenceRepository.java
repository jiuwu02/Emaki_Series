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

public final class ConfiguredSequenceRepository implements SequenceRepository {

    private static final Pattern VAR_REFERENCE = Pattern.compile("%var\\.([A-Za-z0-9_]+)%");

    private final Map<String, Entry> entries;

    private ConfiguredSequenceRepository(Map<String, Entry> entries) {
        this.entries = entries;
    }

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

        Map<String, Entry> entries = new LinkedHashMap<>();
        raw.forEach((name, lines) -> entries.put(name,
                new Entry(name, lines, parametersOf(lines), callsOf(lines), null)));
        ConfiguredSequenceRepository catalog = new ConfiguredSequenceRepository(entries);

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

    public static @NotNull ConfiguredSequenceRepository empty() {
        return new ConfiguredSequenceRepository(Map.of());
    }

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

    public @Nullable List<CompiledPipeline> bodyOf(@Nullable String name) {
        if (name == null) {
            return null;
        }
        Entry entry = entries.get(key(name));
        return entry == null ? null : entry.compiled();
    }

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

    public int size() {
        return entries.size();
    }

    public @NotNull List<String> failed() {
        List<String> names = new ArrayList<>();
        entries.forEach((name, entry) -> {
            if (entry.compiled() == null) {
                names.add(name);
            }
        });
        return List.copyOf(names);
    }

    public interface LineCompiler {

        @Nullable
        CompiledPipeline compile(@NotNull String sequence,
                @NotNull String line,
                @NotNull SequenceRepository catalog);
    }

    private record Entry(@NotNull String name,
            @NotNull List<String> lines,
            @NotNull Set<String> parameters,
            @NotNull Set<String> calls,
            @Nullable List<CompiledPipeline> compiled) {
    }

}
