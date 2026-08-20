package emaki.jiuwu.craft.strengthen.api.model;

import java.util.List;
import java.util.Objects;

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

    /**
     * Builds a result from every pity track that participated in one attempt.
     *
     * <p>This is the multi-track entry point. {@code null} entries are dropped rather than rejected, so a
     * caller assembling tracks from optional counters does not have to pre-filter. Track order is
     * preserved and the first surviving track becomes the compatibility primary track read by
     * {@link #primaryCounter()} and {@link #primaryTrack()}.
     *
     * @param tracks the participating tracks, in the order the runtime evaluated them; {@code null} or an
     *               all-{@code null} list yields {@link #empty()}
     * @return the multi-track result
     */
    public static @NotNull EnhancementPityResult ofTracks(@Nullable List<EnhancementPityTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return empty();
        }
        List<EnhancementPityTrack> retained = tracks.stream().filter(Objects::nonNull).toList();
        return retained.isEmpty() ? empty() : new EnhancementPityResult(retained);
    }

    /** {@return the number of pity tracks carried by this result} */
    public int trackCount() {
        return tracks.size();
    }

    /**
     * Finds one track by its configured group.
     *
     * @param group the counter group to look for; matched case-sensitively against
     *              {@link EnhancementPityTrack#group()}
     * @return the matching track, or {@code null} when this result carries no such group
     */
    public @Nullable EnhancementPityTrack track(@Nullable String group) {
        if (group == null) {
            return null;
        }
        for (EnhancementPityTrack track : tracks) {
            if (group.equals(track.group())) {
                return track;
            }
        }
        return null;
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
