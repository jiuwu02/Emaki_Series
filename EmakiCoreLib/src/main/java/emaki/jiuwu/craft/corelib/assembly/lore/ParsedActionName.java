package emaki.jiuwu.craft.corelib.assembly.lore;

public record ParsedActionName(SearchMode mode, InsertPosition position, int matchIndex) {

    public ParsedActionName {
        if (mode == null) {
            mode = SearchMode.CONTAINS;
        }
        if (position == null) {
            position = InsertPosition.BELOW;
        }
        if (matchIndex <= 0) {
            throw new SearchInsertValidationException(
                    SearchInsertValidationException.Reason.INVALID_MATCH_INDEX,
                    "Lore search insert match index must be >= 1."
            );
        }
    }
}
