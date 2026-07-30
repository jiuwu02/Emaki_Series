package emaki.jiuwu.craft.item.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of an item set definition.
 *
 * @param id          canonical lowercase set id
 * @param displayName display name; falls back to the id when unset
 * @param pieceIds    every piece id belonging to this set, sorted
 * @param thresholds  the equipped-piece counts at which bonuses activate, ascending
 */
public record ItemSetView(@NotNull String id,
                          @NotNull String displayName,
                          @NotNull List<String> pieceIds,
                          @NotNull List<Integer> thresholds) {

    /**
     * Normalises every reference component so no accessor can return {@code null}.
     *
     * @param id          canonical lowercase set id
     * @param displayName display name
     * @param pieceIds    member piece ids
     * @param thresholds  bonus activation thresholds
     */
    public ItemSetView {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        pieceIds = pieceIds == null ? List.of() : List.copyOf(pieceIds);
        thresholds = thresholds == null ? List.of() : List.copyOf(thresholds);
    }

    /** {@return how many pieces this set has} */
    public int totalPieces() {
        return pieceIds.size();
    }

    /**
     * Returns the thresholds a given equipped-piece count satisfies.
     *
     * @param equippedPieces how many pieces of this set are equipped
     * @return the satisfied thresholds, ascending
     */
    public @NotNull List<Integer> activeThresholds(int equippedPieces) {
        return thresholds.stream().filter(threshold -> threshold <= equippedPieces).toList();
    }

}
