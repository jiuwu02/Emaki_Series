package emaki.jiuwu.craft.corelib.api.animation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An immutable, owner-scoped animation definition: a duration, a loop flag, a priority, and a set of
 * {@link AnimationKeyframe}s that fire at fixed tick offsets.
 *
 * <p>The definition carries no model reference. It describes <em>timing and behaviour</em> only; the
 * actual model/animation rendering is delegated to whichever model backend the consumer attaches. The
 * consumer plays the underlying engine animation itself (typically through a {@code 0t} keyframe), and
 * uses the later keyframes to schedule particle, sound, attribute, or state events at the right moment.</p>
 *
 * <p>Ids are normalised to lower {@link Locale#ROOT}. Keyframes are defensively copied, sorted by tick,
 * and clamped to {@code [0, duration]}.</p>
 *
 * @param id              stable identifier, lower-cased; must be non-blank
 * @param engineAnimation the model-backend animation name to render while this definition plays; empty
 *                        when the definition is timing-only. The CoreLib runtime never interprets it;
 *                        consumers receive it through {@link AnimationListener#onPlay} and forward it
 *                        to their model bridge
 * @param durationTicks   total length in ticks; clamped to at least {@code 1}
 * @param loop            whether playback restarts after {@code durationTicks}
 * @param priority        competition tier; see {@link AnimationPriority}
 * @param conflictPolicy  how equal-priority playbacks resolve; see {@link AnimationConflictPolicy}
 * @param keyframes       sorted, clamped keyframes; never {@code null}
 */
public record AnimationDefinition(@NotNull String id,
                                  @NotNull String engineAnimation,
                                  int durationTicks,
                                  boolean loop,
                                  @NotNull AnimationPriority priority,
                                  @NotNull AnimationConflictPolicy conflictPolicy,
                                  @NotNull List<AnimationKeyframe> keyframes) {

    public AnimationDefinition {
        id = normalise(id);
        engineAnimation = engineAnimation == null ? "" : engineAnimation.trim();
        durationTicks = Math.max(1, durationTicks);
        priority = priority == null ? AnimationPriority.AMBIENT : priority;
        conflictPolicy = conflictPolicy == null ? AnimationConflictPolicy.REPLACE : conflictPolicy;
        keyframes = canonicalise(keyframes, durationTicks);
    }

    /**
     * Builds a definition from raw inputs, normalising the id and keyframes.
     *
     * @param id              identifier; blank input yields an invalid definition the registry rejects
     * @param engineAnimation model-backend animation name; {@code null} selects empty
     * @param durationTicks   total length in ticks
     * @param loop            whether playback loops
     * @param priority        competition tier; {@code null} selects {@link AnimationPriority#AMBIENT}
     * @param conflictPolicy  equal-priority resolution; {@code null} selects {@link AnimationConflictPolicy#REPLACE}
     * @param keyframes       keyframes; {@code null} selects an empty list
     * @return a normalised definition
     */
    public static @NotNull AnimationDefinition of(@Nullable String id,
            @Nullable String engineAnimation,
            int durationTicks,
            boolean loop,
            @Nullable AnimationPriority priority,
            @Nullable AnimationConflictPolicy conflictPolicy,
            @Nullable List<AnimationKeyframe> keyframes) {
        return new AnimationDefinition(id == null ? "" : id, engineAnimation, durationTicks, loop, priority,
                conflictPolicy, keyframes);
    }

    /** {@return whether the id is present enough to register} */
    public boolean valid() {
        return !id.isEmpty();
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static List<AnimationKeyframe> canonicalise(List<AnimationKeyframe> source, int duration) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<AnimationKeyframe> copy = new ArrayList<>(source.size());
        for (AnimationKeyframe frame : source) {
            if (frame != null) {
                copy.add(new AnimationKeyframe(Math.min(Math.max(0, frame.tick()), duration),
                        frame.actionLine(), frame.label()));
            }
        }
        copy.sort(Comparator.comparingInt(AnimationKeyframe::tick));
        return List.copyOf(copy);
    }
}
