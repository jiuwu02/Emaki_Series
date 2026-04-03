package emaki.jiuwu.craft.corelib.assembly.lore;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class LoreInsertOperator {

    public List<String> insertAbove(List<String> loreLines, int targetIndex, List<String> content, boolean inheritStyle) {
        return insert(loreLines, targetIndex, referenceLine(loreLines, targetIndex), content, inheritStyle);
    }

    public List<String> insertBelow(List<String> loreLines, int targetIndex, List<String> content, boolean inheritStyle) {
        return insert(loreLines, targetIndex + 1, referenceLine(loreLines, targetIndex), content, inheritStyle);
    }

    public List<String> appendToEnd(List<String> loreLines, List<String> content, boolean inheritStyle) {
        String reference = loreLines == null || loreLines.isEmpty() ? "" : loreLines.get(loreLines.size() - 1);
        return insert(loreLines, loreLines == null ? 0 : loreLines.size(), reference, content, inheritStyle);
    }

    public int resolveTargetIndex(List<Integer> matches, int matchIndex) {
        if (matchIndex <= 0) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_MATCH_INDEX,
                    "Lore search insert match index must be >= 1."
            );
        }
        if (matches == null || matches.isEmpty()) {
            return -1;
        }
        int resolved = Math.min(matchIndex, matches.size()) - 1;
        return matches.get(resolved);
    }

    private List<String> insert(List<String> loreLines,
            int insertionIndex,
            String referenceLine,
            List<String> content,
            boolean inheritStyle) {
        List<String> result = new ArrayList<>();
        if (loreLines != null) {
            result.addAll(loreLines);
        }
        int safeIndex = Math.max(0, Math.min(insertionIndex, result.size()));
        result.addAll(safeIndex, prepareContent(content, referenceLine, inheritStyle));
        return List.copyOf(result);
    }

    private List<String> prepareContent(List<String> content, String referenceLine, boolean inheritStyle) {
        List<String> prepared = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return prepared;
        }
        for (String line : content) {
            prepared.add(LoreTextUtil.inheritStyle(Texts.toStringSafe(line), referenceLine, inheritStyle));
        }
        return prepared;
    }

    private String referenceLine(List<String> loreLines, int targetIndex) {
        if (loreLines == null || loreLines.isEmpty() || targetIndex < 0 || targetIndex >= loreLines.size()) {
            return "";
        }
        return loreLines.get(targetIndex);
    }
}
