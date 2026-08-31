package emaki.jiuwu.craft.corelib.legacy;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class LegacyMatcherFragment {

    private static final String TYPE_ITEM_SOURCE = "type: item_source";

    private LegacyMatcherFragment() {
    }

    public static @NotNull List<String> parseSources(@NotNull List<String> lines,
            @NotNull YamlBlockLocator.Hit hit) {
        return hit.inline()
                ? parseInline(hit.inlineValue())
                : parseBlock(lines, hit);
    }

    private static List<String> parseInline(String raw) {
        String value = Texts.toStringSafe(raw).trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        List<String> result = new ArrayList<>();
        for (String token : value.split(",")) {
            String cleaned = unquote(token);
            if (Texts.isNotBlank(cleaned)) {
                result.add(cleaned);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> parseBlock(List<String> lines, YamlBlockLocator.Hit hit) {
        List<String> result = new ArrayList<>();
        for (int index = hit.startLine() + 1; index < hit.endLine(); index++) {
            String line = lines.get(index);
            if (YamlBlockLocator.skippable(line)) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.startsWith("-")) {
                continue;
            }
            String cleaned = unquote(trimmed.substring(1));
            if (Texts.isNotBlank(cleaned)) {
                result.add(cleaned);
            }
        }
        return List.copyOf(result);
    }

    private static String unquote(String raw) {
        String value = Texts.toStringSafe(raw).trim();
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    public static @NotNull List<String> render(@NotNull List<String> sources,
            @NotNull YamlBlockLocator.Hit hit,
            @NotNull String matcherKey,
            @Nullable List<String> existingMatcher) {
        if (sources.isEmpty()) {
            return List.of();
        }
        String head = head(hit);
        String pad = " ".repeat(hit.keyColumn());
        if (existingMatcher != null && !existingMatcher.isEmpty()) {
            return renderMerged(sources, head, pad, matcherKey, existingMatcher);
        }
        if (sources.size() == 1) {
            List<String> rendered = new ArrayList<>();
            rendered.add(head + matcherKey + ":");
            rendered.add(pad + "  " + TYPE_ITEM_SOURCE);
            rendered.add(pad + "  sources:");
            rendered.add(pad + "    - " + quote(sources.getFirst()));
            return List.copyOf(rendered);
        }
        List<String> rendered = new ArrayList<>();
        rendered.add(head + matcherKey + ":");
        rendered.add(pad + "  " + TYPE_ITEM_SOURCE);
        rendered.add(pad + "  sources:");
        for (String source : sources) {
            rendered.add(pad + "    - " + quote(source));
        }
        return List.copyOf(rendered);
    }

    private static List<String> renderMerged(List<String> sources,
            String head,
            String pad,
            String matcherKey,
            List<String> existingMatcher) {
        List<String> rendered = new ArrayList<>();
        rendered.add(head + matcherKey + ":");
        rendered.add(pad + "  type: all_of");
        rendered.add(pad + "  matchers:");
        rendered.add(pad + "    - " + TYPE_ITEM_SOURCE);
        rendered.add(pad + "      sources:");
        for (String source : sources) {
            rendered.add(pad + "        - " + quote(source));
        }
        for (String line : reindentChild(existingMatcher, pad + "    - ")) {
            rendered.add(line);
        }
        return List.copyOf(rendered);
    }

    private static List<String> reindentChild(List<String> body, String firstPrefix) {
        List<String> cleaned = new ArrayList<>();
        int baseIndent = Integer.MAX_VALUE;
        for (String line : body) {
            if (YamlBlockLocator.skippable(line)) {
                continue;
            }
            baseIndent = Math.min(baseIndent, YamlBlockLocator.indentOf(line));
            cleaned.add(line);
        }
        if (cleaned.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        String continuation = " ".repeat(firstPrefix.length());
        for (int index = 0; index < cleaned.size(); index++) {
            String stripped = cleaned.get(index).substring(baseIndent);
            result.add((index == 0 ? firstPrefix : continuation) + stripped);
        }
        return List.copyOf(result);
    }

    private static String head(YamlBlockLocator.Hit hit) {
        if (!hit.dashLeading() && !(hit.inline() && hit.dashIndent() >= 0)) {
            return " ".repeat(hit.keyColumn());
        }
        return " ".repeat(hit.dashIndent()) + "- ";
    }

    private static String quote(String source) {
        return "\"" + Texts.toStringSafe(source).replace("\"", "\\\"") + "\"";
    }
}
