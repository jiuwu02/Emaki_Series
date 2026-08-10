package emaki.jiuwu.craft.station.dismantle;

import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;

/**
 * One loaded dismantle recipe definition.
 *
 * <p>A dismantle recipe consumes the input item placed by the player and then rolls one or more
 * times against the pool to produce randomised outputs. Each roll independently picks a weighted
 * pool entry and resolves a random amount from that entry's {@link AmountRange}.
 *
 * @param id             the recipe id, unique across the dismantle recipe directory
 * @param displayName    the configured display name, unrendered
 * @param stationId      the station this recipe belongs to; empty means any station
 * @param tags           lower-cased tags used by station include/exclude rules
 * @param inputSource    the item-source reference used to match the player's input item
 * @param rolls          how many rolls to perform per dismantle
 * @param pool           the weighted output pool entries
 * @param permission     the permission required to see and use it, or an empty string
 * @param condition      the gate evaluated before submission
 */
public record DismantleRecipeDefinition(
        String id,
        String displayName,
        String stationId,
        Set<String> tags,
        ItemSourceRef inputSource,
        RollsRange rolls,
        List<DismantlePoolEntry> pool,
        String permission,
        ConditionBlock condition) {

    /**
     * Creates a definition with defensively copied collections.
     *
     * @throws NullPointerException when {@code id}, {@code inputSource}, {@code rolls}, or
     *                              {@code pool} is {@code null}
     */
    public DismantleRecipeDefinition {
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (inputSource == null) {
            throw new NullPointerException("inputSource");
        }
        if (rolls == null) {
            throw new NullPointerException("rolls");
        }
        if (pool == null) {
            throw new NullPointerException("pool");
        }
        displayName = displayName == null ? id : displayName;
        stationId = stationId == null ? "" : stationId;
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        pool = List.copyOf(pool);
        permission = permission == null ? "" : permission;
        condition = condition == null ? ConditionBlock.empty() : condition;
    }

    /** {@return whether this recipe restricts use behind its own permission node} */
    public boolean hasPermission() {
        return !permission.isBlank();
    }

    /** {@return whether this recipe is scoped to a specific station} */
    public boolean hasScopedStation() {
        return !stationId.isBlank();
    }
}
