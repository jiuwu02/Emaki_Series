package emaki.jiuwu.craft.strengthen.api.model;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable administrative view of one stored pity counter record.
 *
 * <p>Unlike {@link EnhancementPityTrack}, which describes a track's participation in one attempt, this
 * view is the persisted record itself: it carries the owner key and last trigger timestamp so an operator
 * can audit a counter without performing an enhancement.
 *
 * @param scope       counter scope, {@code player} or {@code item}
 * @param group       counter group, including any isolation suffix the runtime appended
 * @param ownerKey    owner identity within the scope: a player UUID or an item-instance id
 * @param counter     current counter value
 * @param lastTrigger epoch milliseconds of the last recorded update, or {@code 0} when never updated
 * @param triggered   whether the record was flagged as triggered when it was last written
 */
public record EnhancementPityStateView(@NotNull String scope,
        @NotNull String group,
        @NotNull String ownerKey,
        int counter,
        long lastTrigger,
        boolean triggered) {

    public EnhancementPityStateView {
        scope = scope == null ? "" : scope;
        group = group == null ? "" : group;
        ownerKey = ownerKey == null ? "" : ownerKey;
        counter = Math.max(0, counter);
        lastTrigger = Math.max(0L, lastTrigger);
    }

    /**
     * {@return the group with any isolation suffix removed}
     *
     * <p>The runtime appends {@code #dimension=value} segments when a recipe declares pity isolation.
     * This accessor recovers the group as written in the recipe.
     */
    public @NotNull String baseGroup() {
        int separator = group.indexOf('#');
        return separator < 0 ? group : group.substring(0, separator);
    }

    /** {@return whether this record carries an isolation suffix} */
    public boolean isolated() {
        return group.indexOf('#') >= 0;
    }
}
