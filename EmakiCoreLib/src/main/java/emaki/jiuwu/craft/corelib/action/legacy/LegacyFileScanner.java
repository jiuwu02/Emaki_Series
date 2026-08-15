package emaki.jiuwu.craft.corelib.action.legacy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class LegacyFileScanner {

    private final LegacyLineConverter converter = new LegacyLineConverter();

    @NotNull Result scan(@NotNull String content) {
        String[] lines = content.split("\n", -1);
        List<Change> changes = new ArrayList<>();
        List<Skip> skips = new ArrayList<>();

        Deque<Frame> stack = new ArrayDeque<>();
        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index];
            String stripped = stripLineEnding(raw);
            String trimmed = stripped.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = indentOf(stripped);
            if (!trimmed.startsWith("- ") && !trimmed.equals("-")) {
                updateStack(stack, indent, trimmed);
                continue;
            }
            String parentKey = parentKeyFor(stack, indent);
            if (LegacyMappings.blacklisted(parentKey)) {
                continue;
            }
            Item item = parseItem(stripped);
            if (item == null) {
                continue;
            }
            LegacyLineConverter.Result result = converter.convert(item.value());
            switch (result.status()) {
                case CONVERTED -> changes.add(new Change(index, raw,
                        item.prefix() + reQuoteForYaml(result.pipeline()), item.value(),
                        result.pipeline(), parentKey));
                case UNMAPPABLE -> skips.add(new Skip(index + 1, item.value(),
                        result.oldId(), result.reason()));
                case NOT_AN_ACTION -> {

                }
            }
        }
        return new Result(List.copyOf(changes), List.copyOf(skips), lines);
    }

    @NotNull String rewrite(@NotNull Result result, @NotNull List<Change> applied) {
        String[] lines = result.lines().clone();
        for (Change change : applied) {
            String original = lines[change.lineIndex()];
            String ending = original.endsWith("\r") ? "\r" : "";
            lines[change.lineIndex()] = change.newLine() + ending;
        }
        return String.join("\n", lines);
    }

    private String reQuoteForYaml(String pipeline) {
        if (pipeline.indexOf('"') < 0) {
            return '"' + pipeline + '"';
        }
        if (pipeline.indexOf('\'') < 0) {
            return '\'' + pipeline + '\'';
        }
        return '\'' + pipeline.replace("'", "''") + '\'';
    }

    private @Nullable Item parseItem(String line) {
        int dash = line.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String prefix = line.substring(0, dash + 1);
        String rest = line.substring(dash + 1);
        int leading = 0;
        while (leading < rest.length() && (rest.charAt(leading) == ' ' || rest.charAt(leading) == '\t')) {
            leading++;
        }
        prefix = prefix + rest.substring(0, leading);
        String value = rest.substring(leading);
        if (value.isEmpty() || value.startsWith("#")) {
            return null;
        }

        if (value.endsWith(":") || looksLikeMappingEntry(value)) {
            return null;
        }
        return new Item(prefix, unquote(value));
    }

    private boolean looksLikeMappingEntry(String value) {
        char quote = 0;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
                continue;
            }
            if (ch == ':' && (index + 1 >= value.length() || value.charAt(index + 1) == ' ')) {
                return true;
            }
        }
        return false;
    }

    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if (first == last && (first == '"' || first == '\'')) {
                String inner = trimmed.substring(1, trimmed.length() - 1);
                return first == '\'' ? inner.replace("''", "'") : inner;
            }
        }
        return trimmed;
    }

    private void updateStack(Deque<Frame> stack, int indent, String trimmed) {
        int colon = colonIndex(trimmed);
        if (colon <= 0) {
            return;
        }
        String key = trimmed.substring(0, colon).trim();
        while (!stack.isEmpty() && stack.peekLast().indent() >= indent) {
            stack.removeLast();
        }
        stack.addLast(new Frame(indent, key));
    }

    private int colonIndex(String trimmed) {
        char quote = 0;
        for (int index = 0; index < trimmed.length(); index++) {
            char ch = trimmed.charAt(index);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
                continue;
            }
            if (ch == ':') {
                return index;
            }
        }
        return -1;
    }

    private @Nullable String parentKeyFor(Deque<Frame> stack, int indent) {
        String candidate = null;
        for (Frame frame : stack) {
            if (frame.indent() < indent || frame.indent() == indent) {
                candidate = frame.key();
            }
        }
        return candidate;
    }

    private int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
            indent++;
        }
        return indent;
    }

    private String stripLineEnding(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private record Frame(int indent, @NotNull String key) {
    }

    private record Item(@NotNull String prefix, @NotNull String value) {
    }

    record Change(int lineIndex,
            @NotNull String originalLine,
            @NotNull String newLine,
            @NotNull String oldValue,
            @NotNull String newValue,
            @Nullable String parentKey) {
    }

    record Skip(int lineNumber,
            @NotNull String value,
            @Nullable String oldId,
            @Nullable String reason) {
    }

    record Result(@NotNull List<Change> changes,
            @NotNull List<Skip> skips,
            @NotNull String[] lines) {

        boolean hasChanges() {
            return !changes.isEmpty();
        }
    }
}
