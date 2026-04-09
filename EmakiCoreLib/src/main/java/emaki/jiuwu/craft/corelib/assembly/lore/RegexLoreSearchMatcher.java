package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RegexLoreSearchMatcher implements LoreSearchMatcher {

    private static final int MAX_CACHE_SIZE = 100;
    private final Map<PatternKey, java.util.regex.Pattern> patternCache = new ConcurrentHashMap<>();

    @Override
    public List<Integer> matchAll(List<String> loreLines, String pattern, boolean ignoreCase) {
        List<Integer> matches = new ArrayList<>();
        if (loreLines == null || loreLines.isEmpty()) {
            return matches;
        }
        java.util.regex.Pattern compiled = getOrCreatePattern(pattern, ignoreCase);
        for (int index = 0; index < loreLines.size(); index++) {
            String normalizedLine = LoreTextUtil.stripColorCodes(loreLines.get(index));
            if (compiled.matcher(normalizedLine).find()) {
                matches.add(index);
            }
        }
        return matches;
    }

    private java.util.regex.Pattern getOrCreatePattern(String pattern, boolean ignoreCase) {
        PatternKey key = new PatternKey(pattern, ignoreCase);
        return patternCache.computeIfAbsent(key, k -> {
            if (patternCache.size() >= MAX_CACHE_SIZE) {
                patternCache.clear();
            }
            int flags = ignoreCase ? java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE : 0;
            return java.util.regex.Pattern.compile(LoreTextUtil.stripColorCodes(k.pattern()), flags);
        });
    }

    private record PatternKey(String pattern, boolean ignoreCase) {

    }
}
