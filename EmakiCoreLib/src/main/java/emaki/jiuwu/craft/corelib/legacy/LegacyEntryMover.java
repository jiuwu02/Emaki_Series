package emaki.jiuwu.craft.corelib.legacy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LegacyEntryMover {

    private LegacyEntryMover() {
    }

    public record Spec(@NotNull String legacyKey, @NotNull String effectType, @NotNull String effectKey) {
    }

    public static @NotNull LegacyContentMigrator.Outcome moveRootKeys(@NotNull List<String> lines,
            @NotNull List<Spec> specs) {
        return moveKeysInRange(lines, 0, lines.size(), 0, specs);
    }

    public static @NotNull LegacyContentMigrator.Outcome moveKeysInRange(@NotNull List<String> lines,
            int from,
            int to,
            int keyIndent,
            @NotNull List<Spec> specs) {
        List<String> working = new ArrayList<>(lines);
        List<String> changes = new ArrayList<>();
        List<String> appended = new ArrayList<>();
        List<int[]> removals = new ArrayList<>();
        for (Spec spec : specs) {
            int[] block = findBlock(working, from, to, spec.legacyKey(), keyIndent);
            if (block == null) {
                continue;
            }
            changes.add(spec.legacyKey());
            String inline = inlineValue(working.get(block[0]), keyIndent + spec.legacyKey().length());
            List<String> body = blockBody(working, block);
            removals.add(block);
            if (!body.isEmpty() || !inline.isEmpty()) {
                appended.addAll(renderEntry(spec, inline, body, keyIndent));
            }
        }
        if (removals.isEmpty()) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        int removed = 0;
        removals.sort(Comparator.comparingInt((int[] range) -> range[0]).reversed());
        for (int[] range : removals) {
            working.subList(range[0], range[1]).clear();
            removed += range[1] - range[0];
        }
        if (!appended.isEmpty()) {
            appendEffects(working, from, Math.max(from, to - removed), keyIndent, appended);
        }
        return new LegacyContentMigrator.Outcome(working, changes);
    }

    public static @NotNull LegacyContentMigrator.Outcome renameKeys(@NotNull List<String> lines,
            int from,
            int to,
            int indent,
            @NotNull List<Rename> renames) {
        List<String> working = new ArrayList<>(lines);
        List<String> changes = new ArrayList<>();
        for (Rename rename : renames) {
            int[] block = findBlock(working, from, to, rename.legacyKey(), indent);
            if (block == null) {
                continue;
            }
            int lineIndex = block[0];
            String line = working.get(lineIndex);
            working.set(lineIndex, line.substring(0, indent) + rename.currentKey() + line.substring(indent + rename.legacyKey().length()));
            changes.add(rename.legacyKey());
        }
        if (changes.isEmpty()) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        return new LegacyContentMigrator.Outcome(working, changes);
    }

    public record Rename(@NotNull String legacyKey, @NotNull String currentKey) {
    }

    public static @NotNull LegacyContentMigrator.Outcome hoistToParent(@NotNull List<String> lines,
            @NotNull String legacyKey,
            @NotNull String parentKey,
            @NotNull String currentKey,
            @NotNull java.util.function.UnaryOperator<String> transform) {
        int[] block = findRootBlock(lines, legacyKey);
        if (block == null) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        String inline = inlineValue(lines.get(block[0]), legacyKey.length());
        int bodySize = block[1] - block[0] - 1;
        if (inline.isEmpty() || bodySize > 0) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        String converted = transform.apply(unquote(inline));
        if (converted == null || converted.isBlank()) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        List<String> working = new ArrayList<>(lines);
        int[] parent = findRootBlock(working, parentKey);
        if (parent != null) {
            boolean alreadyDeclared = false;
            for (int index = parent[0] + 1; index < parent[1]; index++) {
                String line = working.get(index);
                int lineIndent = YamlBlockLocator.indentOf(line);
                if (lineIndent != Integer.MAX_VALUE
                        && lineIndent == 2
                        && startsWithKey(line.substring(2), currentKey)) {
                    alreadyDeclared = true;
                    break;
                }
            }
            if (alreadyDeclared) {
                return LegacyContentMigrator.Outcome.unchanged(lines);
            }
            working.subList(block[0], block[1]).clear();
            int[] refreshed = findRootBlock(working, parentKey);
            if (refreshed == null) {
                return LegacyContentMigrator.Outcome.unchanged(lines);
            }
            working.add(refreshed[1], "  " + currentKey + ": " + quoteIfNeeded(converted));
        } else {
            working.subList(block[0], block[1]).clear();
            List<String> block2 = new ArrayList<>();
            if (!working.isEmpty() && !working.getLast().isBlank()) {
                block2.add("");
            }
            block2.add(parentKey + ":");
            block2.add("  " + currentKey + ": " + quoteIfNeeded(converted));
            working.addAll(block2);
        }
        return new LegacyContentMigrator.Outcome(working, List.of(legacyKey));
    }

    private static @NotNull String quoteIfNeeded(@NotNull String value) {
        if (value.isEmpty()) {
            return "\"\"";
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isLetterOrDigit(value.charAt(index)) && "_-.:/".indexOf(value.charAt(index)) < 0) {
                return "\"" + value.replace("\"", "\\\"") + "\"";
            }
        }
        return value;
    }

    public static @NotNull LegacyContentMigrator.Outcome moveIntoComponents(@NotNull List<String> lines,
            @NotNull String legacyKey,
            @NotNull String parentKey,
            @NotNull String componentKey) {
        int[] block = findRootBlock(lines, legacyKey);
        if (block == null) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        String inline = inlineValue(lines.get(block[0]), legacyKey.length());
        List<String> body = blockBody(lines, block);
        if (inline.isEmpty() && body.isEmpty()) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        List<String> working = new ArrayList<>(lines);
        int[] parent = findRootBlock(working, parentKey);
        if (parent == null) {
            List<String> replacement = new ArrayList<>();
            replacement.add(parentKey + ":");
            replacement.add("  components:");
            replacement.add("    " + componentKey + ":" + (inline.isEmpty() ? "" : " " + inline));
            for (String line : body) {
                replacement.add(line.isBlank() ? "" : "    " + line);
            }
            working.subList(block[0], block[1]).clear();
            working.addAll(block[0], replacement);
            return new LegacyContentMigrator.Outcome(working, List.of(legacyKey));
        }
        List<String> fragment = new ArrayList<>();
        fragment.add("    " + componentKey + ":" + (inline.isEmpty() ? "" : " " + inline));
        for (String line : body) {
            fragment.add(line.isBlank() ? "" : "    " + line);
        }
        working.subList(block[0], block[1]).clear();
        int[] refreshed = findRootBlock(working, parentKey);
        if (refreshed == null) {
            return LegacyContentMigrator.Outcome.unchanged(lines);
        }
        int[] components = findBlock(working, refreshed[0] + 1, refreshed[1], "components", 2);
        if (components == null) {
            List<String> block3 = new ArrayList<>();
            block3.add("  components:");
            block3.addAll(fragment);
            working.addAll(refreshed[1], block3);
        } else {
            int insertAt = components[1];
            while (insertAt > components[0] + 1 && working.get(insertAt - 1).isBlank()) {
                insertAt--;
            }
            working.addAll(insertAt, fragment);
        }
        return new LegacyContentMigrator.Outcome(working, List.of(legacyKey));
    }

    public static @NotNull List<String> renameWithinEntries(@NotNull List<String> lines,
            @NotNull String effectType,
            @NotNull String legacyKey,
            @NotNull String currentKey) {
        boolean changed = false;
        List<String> working = new ArrayList<>(lines);
        for (int index = 0; index < working.size(); index++) {
            String line = working.get(index);
            int indent = YamlBlockLocator.indentOf(line);
            if (indent == Integer.MAX_VALUE) {
                continue;
            }
            String rest = line.substring(indent);
            if (rest.startsWith("- ")) {
                indent += 2;
                rest = rest.substring(2).trim();
            }
            if (!startsWithKey(rest, "type")) {
                continue;
            }
            if (!effectType.equals(unquote(inlineValue(line, line.length() - rest.length() + 4)))) {
                continue;
            }
            int boundary = entryBoundary(working, index, indent);
            for (int cursor = index + 1; cursor < boundary; cursor++) {
                String candidate = working.get(cursor);
                int candidateIndent = YamlBlockLocator.indentOf(candidate);
                if (candidateIndent == Integer.MAX_VALUE) {
                    continue;
                }
                if (startsWithKey(candidate.substring(candidateIndent), legacyKey)) {
                    working.set(cursor, candidate.substring(0, candidateIndent)
                            + currentKey + candidate.substring(candidateIndent + legacyKey.length()));
                    changed = true;
                }
            }
            index = boundary - 1;
        }
        return changed ? List.copyOf(working) : lines;
    }

    private static int entryBoundary(@NotNull List<String> lines, int start, int typeIndent) {
        for (int cursor = start + 1; cursor < lines.size(); cursor++) {
            String line = lines.get(cursor);
            int indent = YamlBlockLocator.indentOf(line);
            if (indent == Integer.MAX_VALUE) {
                continue;
            }
            String rest = line.substring(indent);
            if (indent < typeIndent || (indent == typeIndent - 2 && rest.startsWith("- "))) {
                return cursor;
            }
        }
        return lines.size();
    }

    private static @NotNull String unquote(@NotNull String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }
        return trimmed;
    }

    private static @NotNull List<String> renderEntry(@NotNull Spec spec,
            @NotNull String inline,
            @NotNull List<String> body,
            int keyIndent) {
        String pad = " ".repeat(Math.max(0, keyIndent));
        String inner = pad + "  ";
        List<String> rendered = new ArrayList<>();
        rendered.add(pad + "- type: \"" + spec.effectType() + "\"");
        rendered.add(inner + spec.effectKey() + ":" + (inline.isEmpty() ? "" : " " + inline));
        String bodyPad = inner + "  ";
        for (String line : body) {
            rendered.add(line.isBlank() ? "" : bodyPad + line);
        }
        return rendered;
    }

    private static void appendEffects(@NotNull List<String> working,
            int from,
            int to,
            int keyIndent,
            @NotNull List<String> appended) {
        int[] effects = findBlock(working, from, to, "effects", keyIndent);
        if (effects == null) {
            int insertAt = Math.min(Math.max(to, 0), working.size());
            while (insertAt > from && (working.get(insertAt - 1).isBlank())) {
                insertAt--;
            }
            List<String> block = new ArrayList<>();
            if (insertAt > 0 && !working.get(insertAt - 1).isBlank()) {
                block.add("");
            }
            block.add(" ".repeat(Math.max(0, keyIndent)) + "effects:");
            block.addAll(appended);
            working.addAll(insertAt, block);
            return;
        }
        int insertAt = effects[1];
        while (insertAt > effects[0] + 1 && working.get(insertAt - 1).isBlank()) {
            insertAt--;
        }
        working.addAll(insertAt, appended);
    }

    private static @NotNull List<String> blockBody(@NotNull List<String> lines, int[] block) {
        List<String> body = new ArrayList<>();
        for (int index = block[0] + 1; index < block[1]; index++) {
            body.add(lines.get(index));
        }
        while (!body.isEmpty() && body.getLast().isBlank()) {
            body.removeLast();
        }
        int minimum = Integer.MAX_VALUE;
        for (String line : body) {
            if (!line.isBlank()) {
                minimum = Math.min(minimum, YamlBlockLocator.indentOf(line));
            }
        }
        if (minimum == Integer.MAX_VALUE) {
            return List.of();
        }
        if (minimum <= 0) {
            return List.copyOf(body);
        }
        List<String> normalized = new ArrayList<>();
        for (String line : body) {
            normalized.add(line.isBlank() ? "" : line.substring(Math.min(minimum, line.length())));
        }
        return List.copyOf(normalized);
    }

    public static @Nullable int[] findRootBlock(@NotNull List<String> lines, @NotNull String key) {
        return findBlock(lines, 0, lines.size(), key, 0);
    }

    public static @Nullable int[] findBlock(@NotNull List<String> lines,
            int from,
            int to,
            @NotNull String key,
            int indent) {
        int start = -1;
        for (int index = Math.max(0, from); index < Math.min(to, lines.size()); index++) {
            String line = lines.get(index);
            if (line.isBlank() || YamlBlockLocator.indentOf(line) != indent) {
                continue;
            }
            if (!startsWithKey(line.substring(indent), key)) {
                continue;
            }
            start = index;
            break;
        }
        if (start < 0) {
            return null;
        }
        int end = Math.min(to, lines.size());
        for (int index = start + 1; index < Math.min(to, lines.size()); index++) {
            String line = lines.get(index);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int lineIndent = YamlBlockLocator.indentOf(line);
            if (lineIndent > indent) {
                continue;
            }
            if (lineIndent == indent && line.substring(indent).startsWith("-")) {
                continue;
            }
            end = index;
            break;
        }
        while (end > start + 1 && YamlBlockLocator.skippable(lines.get(end - 1))) {
            end--;
        }
        return new int[] {start, end};
    }

    private static @NotNull String inlineValue(@NotNull String line, int afterKey) {
        int cursor = afterKey;
        if (cursor >= line.length() || line.charAt(cursor) != ':') {
            return "";
        }
        String tail = line.substring(cursor + 1).trim();
        return tail.startsWith("#") ? "" : tail;
    }

    public static @NotNull List<int[]> childBlocks(@NotNull List<String> lines, int from, int to, int indent) {
        List<int[]> blocks = new ArrayList<>();
        int cursor = Math.max(0, from);
        int limit = Math.min(to, lines.size());
        while (cursor < limit) {
            String line = lines.get(cursor);
            if (line.isBlank() || YamlBlockLocator.indentOf(line) != indent) {
                cursor++;
                continue;
            }
            String rest = line.substring(indent);
            if (rest.startsWith("-")) {
                cursor++;
                continue;
            }
            int colon = rest.indexOf(':');
            if (colon <= 0) {
                cursor++;
                continue;
            }
            int[] block = findBlock(lines, cursor, limit, rest.substring(0, colon), indent);
            if (block == null) {
                cursor++;
                continue;
            }
            blocks.add(block);
            cursor = Math.max(block[1], cursor + 1);
        }
        return blocks;
    }

    private static boolean startsWithKey(String rest, String key) {
        if (!rest.startsWith(key)) {
            return false;
        }
        int after = key.length();
        return after < rest.length() && rest.charAt(after) == ':' && (after + 1 == rest.length()
                || Character.isWhitespace(rest.charAt(after + 1)));
    }

    public static @NotNull Set<String> rootKeys(@NotNull List<String> lines, @NotNull List<String> candidates) {
        Set<String> present = new LinkedHashSet<>();
        for (String key : candidates) {
            if (findRootBlock(lines, key) != null) {
                present.add(key);
            }
        }
        return present;
    }
}
