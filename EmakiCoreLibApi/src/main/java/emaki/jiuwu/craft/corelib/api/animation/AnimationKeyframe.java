package emaki.jiuwu.craft.corelib.api.animation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One timed marker inside an {@link AnimationDefinition}.
 *
 * <p>A keyframe fires at {@link #tick()} ticks after playback started. Its {@link #actionLine()} is a
 * single EmakiCoreLib action pipeline line (for example {@code spawn_particle ... | play_sound ...});
 * the shared action engine compiles and runs it with the playing entity as caster. A blank action line
 * is a legal no-op marker used purely to notify {@link AnimationListener}s.</p>
 *
 * @param tick       tick offset from playback start, clamped to {@code [0, duration]} by the definition
 * @param actionLine one action pipeline line, or blank for a pure marker
 * @param label      optional stable identifier surfaced to listeners; may be empty
 */
public record AnimationKeyframe(int tick, @NotNull String actionLine, @NotNull String label) {

    /**
     * Canonicalises a keyframe: clamps a negative tick to zero and normalises null text to empty strings.
     *
     * @param tick       tick offset from playback start
     * @param actionLine action pipeline line, {@code null} treated as blank
     * @param label      listener label, {@code null} treated as empty
     * @return a normalised keyframe
     */
    public static @NotNull AnimationKeyframe of(int tick, @Nullable String actionLine, @Nullable String label) {
        return new AnimationKeyframe(Math.max(0, tick),
                actionLine == null ? "" : actionLine,
                label == null ? "" : label);
    }

    /** {@return whether this keyframe carries an executable action line} */
    public boolean executable() {
        return !actionLine.isBlank();
    }
}
