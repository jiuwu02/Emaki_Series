package emaki.jiuwu.craft.corelib.legacy;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class YamlBlockLocator {

    private YamlBlockLocator() {
    }

    public enum Shape {
        DASH_LEADING,
        STANDALONE,
        INLINE
    }

    public record Region(int start, int end) {

        public Region {
            if (end < start) {
                throw new IllegalArgumentException("end must not precede start: " + start + ".." + end);
            }
        }

        public boolean empty() {
            return start >= end;
        }
    }

    public record Hit(int startLine,
            int endLine,
            int keyColumn,
            int dashIndent,
            Shape shape,
            String inlineValue) {

        public Hit {
            shape = shape == null ? Shape.STANDALONE : shape;
            inlineValue = Texts.toStringSafe(inlineValue);
        }

        public boolean dashLeading() {
            return shape == Shape.DASH_LEADING;
        }

        public boolean inline() {
            return shape == Shape.INLINE;
        }
    }

    public static int indentOf(@Nullable String line) {
        if (line == null) {
            return Integer.MAX_VALUE;
        }
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') {
            index++;
        }
        return index == line.length() ? Integer.MAX_VALUE : index;
    }

    public static boolean skippable(@Nullable String line) {
        if (line == null) {
            return true;
        }
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    public record Match(Hit legacy, @Nullable Hit matcher) {
    }

    public static @NotNull List<Hit> locate(@NotNull List<String> lines, @NotNull LegacyTargetSpec spec) {
        List<Hit> hits = new ArrayList<>();
        for (Region region : regionsOf(lines, spec)) {
            Hit hit = findKey(lines, region, spec.legacyKey());
            if (hit != null) {
                hits.add(hit);
            }
        }
        return List.copyOf(hits);
    }

    public static @NotNull List<Match> locateWithSibling(@NotNull List<String> lines,
            @NotNull LegacyTargetSpec spec) {
        List<Match> matches = new ArrayList<>();
        for (Region region : regionsOf(lines, spec)) {
            Hit legacy = findKey(lines, region, spec.legacyKey());
            if (legacy == null) {
                continue;
            }
            matches.add(new Match(legacy, siblingIn(lines, region, spec.matcherKey(), legacy)));
        }
        return List.copyOf(matches);
    }

    private static @Nullable Hit siblingIn(List<String> lines, Region region, String matcherKey, Hit legacy) {
        for (Hit candidate : findKeys(lines, region, matcherKey)) {
            if (candidate.keyColumn() == legacy.keyColumn() && !candidate.inline()) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Region> regionsOf(List<String> lines, LegacyTargetSpec spec) {
        List<Region> regions = List.of(new Region(0, lines.size()));
        for (String segment : spec.segments()) {
            regions = descend(lines, regions, segment);
            if (regions.isEmpty()) {
                return List.of();
            }
        }
        return regions;
    }

    private static List<Region> descend(List<String> lines, List<Region> regions, String segment) {
        List<Region> result = new ArrayList<>();
        boolean list = LegacyTargetSpec.listSegment(segment);
        boolean wildcard = LegacyTargetSpec.wildcardSegment(segment);
        String name = LegacyTargetSpec.segmentName(segment);
        for (Region region : regions) {
            for (Region child : childRegions(lines, region, name, wildcard)) {
                if (list) {
                    result.addAll(listItemRegions(lines, child));
                } else {
                    result.add(child);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<Region> childRegions(List<String> lines, Region region, String name, boolean wildcard) {
        List<Region> result = new ArrayList<>();
        int cursor = region.start();
        while (cursor < region.end()) {
            String line = lines.get(cursor);
            if (skippable(line)) {
                cursor++;
                continue;
            }
            int indent = indentOf(line);
            String key = mappingKey(line, indent);
            if (key != null && (wildcard || key.equals(name))) {
                int end = blockEnd(lines, cursor + 1, region.end(), indent);
                result.add(new Region(cursor + 1, end));
                cursor = end;
                continue;
            }
            cursor++;
        }
        return result;
    }

    private static String mappingKey(String line, int indent) {
        if (indent == Integer.MAX_VALUE || indent >= line.length()) {
            return null;
        }
        String body = line.substring(indent);
        if (body.startsWith("-")) {
            return null;
        }
        int colon = body.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String key = body.substring(0, colon).trim();
        return key.isEmpty() || key.contains(" ") ? null : key;
    }

    private static int blockEnd(List<String> lines, int from, int limit, int parentIndent) {
        for (int index = from; index < limit; index++) {
            if (skippable(lines.get(index))) {
                continue;
            }
            if (indentOf(lines.get(index)) <= parentIndent) {
                return index;
            }
        }
        return limit;
    }

    private static List<Region> listItemRegions(List<String> lines, Region region) {
        List<Region> result = new ArrayList<>();
        int dashIndent = -1;
        int start = -1;
        for (int index = region.start(); index < region.end(); index++) {
            String line = lines.get(index);
            if (skippable(line)) {
                continue;
            }
            int indent = indentOf(line);
            if (indent >= line.length() || line.charAt(indent) != '-') {
                continue;
            }
            if (dashIndent < 0) {
                dashIndent = indent;
            }
            if (indent != dashIndent) {
                continue;
            }
            if (start >= 0) {
                result.add(new Region(start, index));
            }
            start = index;
        }
        if (start >= 0) {
            result.add(new Region(start, region.end()));
        }
        return List.copyOf(result);
    }

    private static List<Hit> findKeys(List<String> lines, Region region, String legacyKey) {
        List<Hit> hits = new ArrayList<>();
        int cursor = region.start();
        while (cursor < region.end()) {
            String line = lines.get(cursor);
            if (skippable(line)) {
                cursor++;
                continue;
            }
            Hit hit = matchDashLeading(lines, region, cursor, line, legacyKey);
            if (hit == null) {
                hit = matchStandalone(lines, region, cursor, line, legacyKey);
            }
            if (hit == null) {
                cursor++;
                continue;
            }
            hits.add(hit);
            cursor = Math.max(hit.endLine(), cursor + 1);
        }
        return List.copyOf(hits);
    }

    private static Hit findKey(List<String> lines, Region region, String legacyKey) {
        for (int index = region.start(); index < region.end(); index++) {
            String line = lines.get(index);
            if (skippable(line)) {
                continue;
            }
            Hit hit = matchDashLeading(lines, region, index, line, legacyKey);
            if (hit != null) {
                return hit;
            }
            hit = matchStandalone(lines, region, index, line, legacyKey);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static Hit matchDashLeading(List<String> lines,
            Region region,
            int index,
            String line,
            String legacyKey) {
        int indent = indentOf(line);
        if (indent >= line.length() || line.charAt(indent) != '-') {
            return null;
        }
        int cursor = indent + 1;
        while (cursor < line.length() && line.charAt(cursor) == ' ') {
            cursor++;
        }
        if (cursor == indent + 1) {
            return null;
        }
        String rest = line.substring(cursor);
        if (!startsWithKey(rest, legacyKey)) {
            return null;
        }
        int keyColumn = cursor;
        String inline = inlineValue(rest, legacyKey);
        if (Texts.isNotBlank(inline)) {
            return new Hit(index, index + 1, keyColumn, indent, Shape.INLINE, inline);
        }
        int end = blockEnd(lines, index + 1, region.end(), keyColumn);
        return new Hit(index, trimTrailing(lines, index, end), keyColumn, indent, Shape.DASH_LEADING, "");
    }

    private static Hit matchStandalone(List<String> lines,
            Region region,
            int index,
            String line,
            String legacyKey) {
        int indent = indentOf(line);
        if (indent == Integer.MAX_VALUE || indent >= line.length() || line.charAt(indent) == '-') {
            return null;
        }
        String rest = line.substring(indent);
        if (!startsWithKey(rest, legacyKey)) {
            return null;
        }
        String inline = inlineValue(rest, legacyKey);
        if (Texts.isNotBlank(inline)) {
            return new Hit(index, index + 1, indent, -1, Shape.INLINE, inline);
        }
        int end = blockEnd(lines, index + 1, region.end(), indent);
        return new Hit(index, trimTrailing(lines, index, end), indent, -1, Shape.STANDALONE, "");
    }

    private static boolean startsWithKey(String rest, String legacyKey) {
        if (!rest.startsWith(legacyKey)) {
            return false;
        }
        int after = legacyKey.length();
        return after < rest.length() && rest.charAt(after) == ':';
    }

    private static String inlineValue(String rest, String legacyKey) {
        String tail = rest.substring(legacyKey.length() + 1).trim();
        return tail.startsWith("#") ? "" : tail;
    }

    private static int trimTrailing(List<String> lines, int start, int end) {
        int cursor = end;
        while (cursor > start + 1 && skippable(lines.get(cursor - 1))) {
            cursor--;
        }
        return cursor;
    }
}
