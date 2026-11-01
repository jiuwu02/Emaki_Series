package emaki.jiuwu.craft.corelib.legacy;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class LegacyMatcherFragment {

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
            if (cleaned.startsWith("item_source:")) {
                cleaned = unquote(cleaned.substring("item_source:".length()));
            }
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
            @NotNull String sourcesKey) {
        if (sources.isEmpty()) {
            return List.of();
        }
        String head = head(hit);
        String pad = " ".repeat(hit.keyColumn());
        List<String> rendered = new ArrayList<>();
        if ("item_sources".equals(sourcesKey)) {
            rendered.add(head + sourcesKey + ":");
            for (String source : sources) {
                rendered.add(pad + "  - " + quote(source));
            }
            return List.copyOf(rendered);
        }
        if ("item_source".equals(sourcesKey)) {
            if (sources.size() != 1) {
                return List.of();
            }
            rendered.add(head + sourcesKey + ": " + quote(sources.getFirst()));
            return List.copyOf(rendered);
        }
        rendered.add(head + sourcesKey + ":");
        rendered.add(pad + "  type: item_source");
        rendered.add(pad + "  sources:");
        for (String source : sources) {
            rendered.add(pad + "    - " + quote(source));
        }
        return List.copyOf(rendered);
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
