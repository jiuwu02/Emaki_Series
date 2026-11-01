package emaki.jiuwu.craft.corelib.api.animation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Coarse ordering used when several animations compete for the same entity.
 *
 * <p>A playback with a strictly higher ordinal pre-empts (cancels) the currently playing lower-priority
 * animation. Equal priorities are resolved by the definition's own conflict policy. The five levels are
 * fixed so consumers can reason about interruption without reading another plugin's numbers.</p>
 */
public enum AnimationPriority {

    /** Ambient looping states such as idle, walk and run. */
    AMBIENT,
    /** Player-requested or scripted one-shot states such as interact and skill. */
    UTILITY,
    /** Combat one-shots such as attack and cast. */
    ACTION,
    /** Reactive one-shots such as hurt. */
    REACTIVE,
    /** Terminal states such as death; nothing may pre-empt them. */
    TERMINAL;

    /**
     * Parses a priority name case-insensitively.
     *
     * @param value the name to parse; {@code null} or blank selects {@link #AMBIENT}
     * @return the matching priority, defaulting to {@link #AMBIENT}
     */
    public static @NotNull AnimationPriority parse(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return AMBIENT;
        }
        for (AnimationPriority candidate : values()) {
            if (candidate.name().equalsIgnoreCase(value.trim())) {
                return candidate;
            }
        }
        return AMBIENT;
    }
}
