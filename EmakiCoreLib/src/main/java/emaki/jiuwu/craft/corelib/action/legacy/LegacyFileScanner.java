package emaki.jiuwu.craft.corelib.action.legacy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds and rewrites the old action lines inside one YAML file's text.
 *
 * <p>Works on raw lines rather than a parsed YAML tree. The files being migrated are shipped examples
 * carrying more comment than data, and every YAML library available here drops comments on write, so a
 * DOM round-trip would destroy the part of the file server owners actually read.</p>
 *
 * <p>The trade-off is that this class has to track block structure itself. It only needs enough of it
 * to know which key a list item sits under, which is what the {@code lore} blacklist depends on.</p>
 */
final class LegacyFileScanner {

    private final LegacyLineConverter converter = new LegacyLineConverter();

    /**
     * Scans one file's content.
     *
     * @param content the file text
     * @return what the scan found and what the file would become
     */
    @NotNull Result scan(@NotNull String content) {
        String[] lines = content.split("\n", -1);
        List<Change> changes = new ArrayList<>();
        List<Skip> skips = new ArrayList<>();
        // Each frame is the indentation and key of an open mapping entry, nearest last.
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
                    // Left untouched on purpose: this is the heuristic declining, not a failure.
                }
            }
        }
        return new Result(List.copyOf(changes), List.copyOf(skips), lines);
    }

    /**
     * Applies the changes to produce the new file text.
     *
     * @param result a previous scan of the same content
     * @return the rewritten text
     */
    @NotNull String rewrite(@NotNull Result result) {
        String[] lines = result.lines().clone();
        for (Change change : result.changes()) {
            String original = lines[change.lineIndex()];
            String ending = original.endsWith("\r") ? "\r" : "";
            lines[change.lineIndex()] = change.newLine() + ending;
        }
        return String.join("\n", lines);
    }

    /**
     * Chooses the YAML quoting for a pipeline line.
     *
     * <p>Single quotes when the line contains a double quote, because a YAML double-quoted scalar would
     * then need backslash escapes that the pipeline parser would receive verbatim. A line holding both
     * quote styles is emitted single-quoted with YAML's doubled-apostrophe escape.</p>
     */
    private String reQuoteForYaml(String pipeline) {
        if (pipeline.indexOf('"') < 0) {
            return '"' + pipeline + '"';
        }
        if (pipeline.indexOf('\'') < 0) {
            return '\'' + pipeline + '\'';
        }
        return '\'' + pipeline.replace("'", "''") + '\'';
    }

    /** Splits a list item into its {@code - } prefix and its scalar value, unquoting the value. */
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
        // A nested mapping or list, not a scalar: `- id: foo` is structure, not an action line.
        if (value.endsWith(":") || looksLikeMappingEntry(value)) {
            return null;
        }
        return new Item(prefix, unquote(value));
    }

    /**
     * Reports whether a list item is really a mapping entry such as {@code - id: overeat}.
     *
     * <p>Checked before the colon inside any quotes, so that an action line whose text contains a colon
     * is not mistaken for structure.</p>
     */
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

    /** Finds the key a list item at this indentation belongs to. */
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

    /**
     * One open mapping entry.
     *
     * @param indent its indentation
     * @param key its key
     */
    private record Frame(int indent, @NotNull String key) {
    }

    /**
     * A list item split into prefix and value.
     *
     * @param prefix everything up to and including the dash and following spaces
     * @param value the unquoted scalar
     */
    private record Item(@NotNull String prefix, @NotNull String value) {
    }

    /**
     * One line that will be rewritten.
     *
     * @param lineIndex zero-based index into the file's lines
     * @param originalLine the untouched source line
     * @param newLine the replacement line, including indentation and quoting
     * @param oldValue the old action line, unquoted
     * @param newValue the converted pipeline line, unquoted
     * @param parentKey the YAML key this item sits under
     */
    record Change(int lineIndex,
            @NotNull String originalLine,
            @NotNull String newLine,
            @NotNull String oldValue,
            @NotNull String newValue,
            @Nullable String parentKey) {
    }

    /**
     * One recognised old action line that cannot be converted.
     *
     * @param lineNumber one-based line number
     * @param value the old action line
     * @param oldId the old action id
     * @param reason why it cannot be converted
     */
    record Skip(int lineNumber,
            @NotNull String value,
            @Nullable String oldId,
            @Nullable String reason) {
    }

    /**
     * What one file scan produced.
     *
     * @param changes the lines that would be rewritten
     * @param skips the recognised lines that cannot be converted
     * @param lines the file's original lines
     */
    record Result(@NotNull List<Change> changes,
            @NotNull List<Skip> skips,
            @NotNull String[] lines) {

        /** {@return whether this file has anything to convert} */
        boolean hasChanges() {
            return !changes.isEmpty();
        }
    }
}
