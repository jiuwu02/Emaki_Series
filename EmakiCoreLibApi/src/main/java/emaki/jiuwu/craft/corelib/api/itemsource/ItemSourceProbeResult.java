package emaki.jiuwu.craft.corelib.api.itemsource;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Outcome of probing one item source reference.
 *
 * @param state which outcome occurred
 * @param ref the probed reference, {@code null} when the input could not even be parsed
 * @param providerId key of the provider that produced this outcome, or an empty string
 * @param detail human-readable diagnostic, never {@code null}
 */
public record ItemSourceProbeResult(@NotNull ItemSourceProbeState state,
        @Nullable ItemSourceRef ref,
        @NotNull String providerId,
        @NotNull String detail) {

    /** Normalises the two text fields and defaults a missing state to {@code RESOLUTION_ERROR}. */
    public ItemSourceProbeResult {
        state = state == null ? ItemSourceProbeState.RESOLUTION_ERROR : state;
        providerId = providerId == null ? "" : providerId.trim();
        detail = detail == null ? "" : detail.trim();
    }

    /** {@return whether the reference resolves right now} */
    public boolean ready() {
        return state == ItemSourceProbeState.READY;
    }

    /**
     * Creates a result.
     *
     * @param state which outcome occurred
     * @param ref the probed reference
     * @param providerId key of the reporting provider
     * @param detail human-readable diagnostic
     * @return the result
     */
    public static @NotNull ItemSourceProbeResult of(@Nullable ItemSourceProbeState state,
            @Nullable ItemSourceRef ref,
            @Nullable String providerId,
            @Nullable String detail) {
        return new ItemSourceProbeResult(state, ref, providerId, detail);
    }

    /**
     * Creates a successful result.
     *
     * @param ref the resolved reference
     * @param providerId key of the reporting provider
     * @return the result
     */
    public static @NotNull ItemSourceProbeResult ready(@Nullable ItemSourceRef ref, @Nullable String providerId) {
        return of(ItemSourceProbeState.READY, ref, providerId, "");
    }

    /**
     * Creates a result for a reference no provider claims.
     *
     * @param ref the unclaimed reference
     * @return the result
     */
    public static @NotNull ItemSourceProbeResult providerMissing(@Nullable ItemSourceRef ref) {
        return of(ItemSourceProbeState.PROVIDER_MISSING, ref, "",
                "No registered provider supplies this item source.");
    }
}
