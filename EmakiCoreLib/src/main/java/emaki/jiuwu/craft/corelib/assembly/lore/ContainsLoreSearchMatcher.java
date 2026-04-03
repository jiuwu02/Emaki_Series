package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.ArrayList;
import java.util.List;

public final class ContainsLoreSearchMatcher implements LoreSearchMatcher {

    @Override
    public List<Integer> matchAll(List<String> loreLines, String pattern, boolean ignoreCase) {
        List<Integer> matches = new ArrayList<>();
        String normalizedPattern = LoreTextUtil.normalizeSearchText(pattern, ignoreCase);
        if (normalizedPattern.isEmpty() || loreLines == null || loreLines.isEmpty()) {
            return matches;
        }
        for (int index = 0; index < loreLines.size(); index++) {
            String normalizedLine = LoreTextUtil.normalizeSearchText(loreLines.get(index), ignoreCase);
            if (normalizedLine.contains(normalizedPattern)) {
                matches.add(index);
            }
        }
        return matches;
    }
}
