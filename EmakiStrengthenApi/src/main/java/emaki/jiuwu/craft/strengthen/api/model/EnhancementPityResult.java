package emaki.jiuwu.craft.strengthen.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable multi-track pity result for one enhancement attempt.
 *
 * <p>The first track is the compatibility primary track used by the legacy scalar accessors on
 * {@link EnhancementAttemptOutcome}. New callers should inspect {@link #tracks()} when more than one
 * pity counter may participate.
 */
public record EnhancementPityResult(@NotNull List<EnhancementPityTrack> tracks) {

    public EnhancementPityResult {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }

    public static @NotNull EnhancementPityResult empty() {
        return new EnhancementPityResult(List.of());
    }

    public static @NotNull EnhancementPityResult of(@Nullable EnhancementPityTrack track) {
        return track == null ? empty() : new EnhancementPityResult(List.of(track));
    }

    /** {@return whether at least one pity track contributed to the result} */
    public boolean triggered() {
        return tracks.stream().anyMatch(EnhancementPityTrack::triggered);
    }

    /** {@return the compatibility counter from the first track, or {@code 0} when no track exists} */
    public int primaryCounter() {
        return tracks.isEmpty() ? 0 : tracks.get(0).counter();
    }

    /** {@return the first track, or {@code null} when no track exists} */
    public @Nullable EnhancementPityTrack primaryTrack() {
        return tracks.isEmpty() ? null : tracks.get(0);
    }
}
