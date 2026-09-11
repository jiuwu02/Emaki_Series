package emaki.jiuwu.craft.corelib.api.animation;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handle for one in-flight playback of an {@link AnimationDefinition} on a single entity.
 *
 * <p>Obtained from a successful {@code playAnimationAsync} call. All methods are safe from any thread;
 * {@link #cancel()} is idempotent and never throws after the playback has already finished.</p>
 */
public interface AnimationPlaybackHandle {

    /** Lifecycle state of a playback. */
    enum State {
        /** The playback is scheduled and advancing. */
        PLAYING,
        /** The playback ran to its full duration (a non-looping animation finished normally). */
        FINISHED,
        /** The playback was cancelled, pre-empted by a higher-priority animation, or its entity became invalid. */
        CANCELLED
    }

    /** {@return the playback-local unique id} */
    @NotNull UUID playbackId();

    /** {@return the id of the definition being played, lower-cased} */
    @NotNull String definitionId();

    /** {@return the entity UUID this playback is bound to} */
    @NotNull UUID entityId();

    /** {@return the current lifecycle state} */
    @NotNull State state();

    /** {@return whether the playback is still advancing} */
    default boolean playing() {
        return state() == State.PLAYING;
    }

    /** Cancels this playback; keyframes stop firing. Idempotent. */
    void cancel();

    /**
     * Creates an inert handle used when CoreLib is unavailable.
     *
     * @param definitionId definition id to echo back, {@code null} selects empty
     * @return a handle already in {@link State#CANCELLED} whose {@link #cancel()} is a no-op
     */
    static @NotNull AnimationPlaybackHandle inactive(@Nullable String definitionId) {
        return new AnimationPlaybackHandle() {

            private final UUID playback = new UUID(0L, 0L);

            @Override
            public @NotNull UUID playbackId() {
                return playback;
            }

            @Override
            public @NotNull String definitionId() {
                return definitionId == null ? "" : definitionId;
            }

            @Override
            public @NotNull UUID entityId() {
                return playback;
            }

            @Override
            public @NotNull State state() {
                return State.CANCELLED;
            }

            @Override
            public void cancel() {
                // no-op
            }
        };
    }
}
