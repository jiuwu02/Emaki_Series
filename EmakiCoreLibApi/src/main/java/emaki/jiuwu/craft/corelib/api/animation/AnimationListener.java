package emaki.jiuwu.craft.corelib.api.animation;

import java.util.UUID;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Observer for animation playback lifecycle, registered through
 * {@code EmakiCoreLibApi.addAnimationListener(owner, listener)}.
 *
 * <p><strong>Thread:</strong> every callback runs on the playing entity's owner thread, inside the
 * playback scheduler tick. Do not block; schedule long work away from the callback. The entity is the
 * live Bukkit entity the playback is bound to and is guaranteed non-null at dispatch time.</p>
 *
 * <p>All methods have empty defaults so implementors override only the moments they care about. A
 * listener whose owner plugin is disabled is dropped automatically.</p>
 */
public interface AnimationListener {

    /**
     * Called when a playback starts.
     *
     * @param entity      the playing entity
     * @param definition  the definition being played, including its {@code engineAnimation} name for
     *                    consumers that forward playback to a model backend
     * @param playbackId  playback-local unique id
     */
    default void onPlay(@NotNull Entity entity, @NotNull AnimationDefinition definition, @NotNull UUID playbackId) {
    }

    /**
     * Called when a keyframe fires.
     *
     * @param entity      the playing entity
     * @param definitionId lower-cased definition id
     * @param playbackId  playback-local unique id
     * @param keyframe    the keyframe that fired
     */
    default void onKeyframe(@NotNull Entity entity, @NotNull String definitionId, @NotNull UUID playbackId,
            @NotNull AnimationKeyframe keyframe) {
    }

    /**
     * Called exactly once when a playback leaves the {@link AnimationPlaybackHandle.State#PLAYING} state.
     *
     * @param entity       the playing entity, or {@code null} when the entity was already removed
     * @param definitionId lower-cased definition id
     * @param playbackId   playback-local unique id
     * @param state        terminal state: {@code FINISHED} or {@code CANCELLED}
     */
    default void onStop(@Nullable Entity entity, @NotNull String definitionId, @NotNull UUID playbackId,
            @NotNull AnimationPlaybackHandle.State state) {
    }
}
