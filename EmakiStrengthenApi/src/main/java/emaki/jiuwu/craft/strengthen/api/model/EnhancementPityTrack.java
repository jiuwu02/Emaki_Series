package emaki.jiuwu.craft.strengthen.api.model;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable public view of one pity counter involved in an enhancement attempt.
 *
 * @param scope     counter scope, for example {@code player} or {@code item}
 * @param group     configured counter group identifying the track
 * @param counter   counter value after a committed attempt, or the current value in a preview-derived result
 * @param triggered whether this track affected the attempt
 */
public record EnhancementPityTrack(@NotNull String scope,
        @NotNull String group,
        int counter,
        boolean triggered) {

    public EnhancementPityTrack {
        scope = scope == null ? "" : scope;
        group = group == null ? "" : group;
        counter = Math.max(0, counter);
    }
}
