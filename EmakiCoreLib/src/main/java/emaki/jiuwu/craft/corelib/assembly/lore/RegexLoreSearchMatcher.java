package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class RegexLoreSearchMatcher implements LoreSearchMatcher {

    @Override
    public List<Integer> matchAll(List<String> loreLines, String pattern, boolean ignoreCase) {
        List<Integer> matches = new ArrayList<>();
        if (loreLines == null || loreLines.isEmpty()) {
            return matches;
        }
        Pattern compiled = compile(pattern, ignoreCase);
        for (int index = 0; index < loreLines.size(); index++) {
            String normalizedLine = LoreTextUtil.stripColorCodes(loreLines.get(index));
            if (compiled.matcher(normalizedLine).find()) {
                matches.add(index);
            }
        }
        return matches;
    }

    private Pattern compile(String pattern, boolean ignoreCase) {
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        return Pattern.compile(LoreTextUtil.stripColorCodes(pattern), flags);
    }
}
