package emaki.jiuwu.craft.corelib.legacy;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public record LegacyTargetSpec(String directory,
        String path,
        String legacyKey,
        String matcherKey,
        MergeMode mergeMode) {

    public static final String WILDCARD = "*";
    public static final String LIST_MARKER = "[]";

    public enum MergeMode {
        REPLACE,
        MERGE_AND
    }

    public LegacyTargetSpec {
        directory = Texts.toStringSafe(directory);
        path = Texts.toStringSafe(path);
        legacyKey = Texts.toStringSafe(legacyKey);
        matcherKey = Texts.isBlank(matcherKey) ? "matcher" : Texts.toStringSafe(matcherKey);
        mergeMode = mergeMode == null ? MergeMode.REPLACE : mergeMode;
        if (legacyKey.isBlank()) {
            throw new IllegalArgumentException("legacyKey must not be blank");
        }
    }

    public static @NotNull LegacyTargetSpec replace(@Nullable String directory,
            @NotNull String path,
            @NotNull String legacyKey) {
        return new LegacyTargetSpec(directory, path, legacyKey, "matcher", MergeMode.REPLACE);
    }

    public static @NotNull LegacyTargetSpec mergeAnd(@Nullable String directory,
            @NotNull String path,
            @NotNull String legacyKey,
            @NotNull String matcherKey) {
        return new LegacyTargetSpec(directory, path, legacyKey, matcherKey, MergeMode.MERGE_AND);
    }

    public @NotNull List<String> segments() {
        List<String> segments = new ArrayList<>();
        for (String raw : path.split("\\.")) {
            String segment = raw.trim();
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return List.copyOf(segments);
    }

    public boolean rootLevel() {
        return segments().isEmpty();
    }

    public static boolean listSegment(@Nullable String segment) {
        return segment != null && segment.endsWith(LIST_MARKER);
    }

    public static @NotNull String segmentName(@Nullable String segment) {
        String value = Texts.toStringSafe(segment);
        return value.endsWith(LIST_MARKER)
                ? value.substring(0, value.length() - LIST_MARKER.length())
                : value;
    }

    public static boolean wildcardSegment(@Nullable String segment) {
        return WILDCARD.equals(segmentName(segment));
    }
}
