package emaki.jiuwu.craft.item.api.model;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.jetbrains.annotations.NotNull;

/**
 * What EmakiItem has recorded on a concrete item stack.
 *
 * <p>Read from the stack's persistent data, so this reflects the item as it exists now rather than what
 * its definition currently says. A stack created before a configuration change keeps its old recorded
 * state until it is refreshed.
 *
 * <p>Internal signature fields are not exposed: they exist so EmakiItem can detect that a definition
 * changed, and their format is free to change between versions.
 *
 * @param definitionId  canonical lowercase definition id; empty when the stack is not an EmakiItem item
 * @param updateVersion the definition revision this stack was last built against
 * @param setId         the item set recorded on this stack; empty when none
 * @param setPieceId    the set piece recorded on this stack; empty when none
 * @param setActive     how many pieces of the set were equipped when this stack was last refreshed
 * @param setTotal      how many pieces the set has in total
 * @param setThresholds the set bonus thresholds that were active at the last refresh
 */
public record ItemIdentity(@NotNull String definitionId,
                           int updateVersion,
                           @NotNull String setId,
                           @NotNull String setPieceId,
                           @NotNull OptionalInt setActive,
                           @NotNull OptionalInt setTotal,
                           @NotNull List<Integer> setThresholds) {

    private static final ItemIdentity NONE = new ItemIdentity("", 0, "", "",
            OptionalInt.empty(), OptionalInt.empty(), List.of());

    /**
     * Normalises every reference component so no accessor can return {@code null}.
     *
     * @param definitionId  definition id
     * @param updateVersion definition revision
     * @param setId         recorded set id
     * @param setPieceId    recorded set piece id
     * @param setActive     equipped piece count at last refresh
     * @param setTotal      total piece count
     * @param setThresholds active bonus thresholds
     */
    public ItemIdentity {
        definitionId = definitionId == null ? "" : definitionId;
        updateVersion = Math.max(0, updateVersion);
        setId = setId == null ? "" : setId;
        setPieceId = setPieceId == null ? "" : setPieceId;
        setActive = setActive == null ? OptionalInt.empty() : setActive;
        setTotal = setTotal == null ? OptionalInt.empty() : setTotal;
        setThresholds = setThresholds == null ? List.of() : List.copyOf(setThresholds);
    }

    /** {@return the shared value describing a stack EmakiItem does not manage} */
    public static @NotNull ItemIdentity none() {
        return NONE;
    }

    /** {@return whether EmakiItem manages this stack} */
    public boolean managed() {
        return !definitionId.isEmpty();
    }

    /** {@return the definition id when EmakiItem manages this stack} */
    public @NotNull Optional<String> definition() {
        return definitionId.isEmpty() ? Optional.empty() : Optional.of(definitionId);
    }

    /** {@return whether this stack carries recorded item set state} */
    public boolean partOfSet() {
        return !setId.isEmpty();
    }
}
