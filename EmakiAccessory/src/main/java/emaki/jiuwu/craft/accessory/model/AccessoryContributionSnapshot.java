package emaki.jiuwu.craft.accessory.model;

import java.util.List;
import java.util.Map;

/**
 * Precomputed accessory contributions for one player.
 *
 * <p>This type exists because of a hard performance constraint on the EmakiAttribute side: the combat
 * snapshot collector calls {@code AttributeContributionProvider.collect} while building its cache
 * signature, which happens on every combat snapshot read and <em>before</em> the cache hit is decided.
 * A cache hit therefore cannot avoid the call. So {@code collect} must be an O(1) read of an
 * already-built snapshot, and all real work - PDC parsing, {@code itemSnapshot} calls, set threshold
 * evaluation - happens when accessory contents change instead.
 *
 * <p>Both collections are immutable and pre-sized so a hot-path read allocates nothing.
 *
 * @param attributes    attribute id to summed value, including {@code $range_spread} companion keys
 * @param skills        skill id to the slot instance id credited as its source
 * @param setPieceCount accessory set id to equipped piece count, orphans excluded
 */
public record AccessoryContributionSnapshot(Map<String, Double> attributes,
        Map<String, String> skills,
        Map<String, Integer> setPieceCount) {

    private static final AccessoryContributionSnapshot EMPTY =
            new AccessoryContributionSnapshot(Map.of(), Map.of(), Map.of());

    /** Canonical constructor; defends every map against later mutation. */
    public AccessoryContributionSnapshot {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        skills = skills == null ? Map.of() : Map.copyOf(skills);
        setPieceCount = setPieceCount == null ? Map.of() : Map.copyOf(setPieceCount);
    }

    /**
     * {@return the shared empty snapshot, used while a player's data has not finished loading}
     */
    public static AccessoryContributionSnapshot empty() {
        return EMPTY;
    }

    /** {@return whether this snapshot grants nothing at all} */
    public boolean isEmpty() {
        return attributes.isEmpty() && skills.isEmpty();
    }

    /** {@return the accessory set ids that currently have at least one equipped piece} */
    public List<String> setIds() {
        return List.copyOf(setPieceCount.keySet());
    }
}
