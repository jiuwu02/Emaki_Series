package emaki.jiuwu.craft.corelib.api.animation;

import org.jetbrains.annotations.NotNull;

/**
 * How the runtime resolves two animations of equal {@link AnimationPriority} competing for one entity.
 */
public enum AnimationConflictPolicy {

    /** The newer playback replaces the currently playing equal-priority animation. */
    REPLACE,
    /** The newer playback is rejected while an equal-priority animation is still playing. */
    REJECT;

    /**
     * Parses a policy name case-insensitively.
     *
     * @param value the name to parse; {@code null}, blank, or unknown selects {@link #REPLACE}
     * @return the matching policy
     */
    public static @NotNull AnimationConflictPolicy parse(@NotNull String value) {
        for (AnimationConflictPolicy candidate : values()) {
            if (candidate.name().equalsIgnoreCase(value == null ? "" : value.trim())) {
                return candidate;
            }
        }
        return REPLACE;
    }
}
