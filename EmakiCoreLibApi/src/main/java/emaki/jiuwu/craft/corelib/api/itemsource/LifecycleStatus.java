package emaki.jiuwu.craft.corelib.api.itemsource;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * How far along a provider's backing plugin is, plus why.
 *
 * @param state the lifecycle stage
 * @param detail human-readable reason, never {@code null}
 */
public record LifecycleStatus(@NotNull LifecycleState state, @NotNull String detail) {

    /** Normalises the detail text and defaults a missing state to {@code ABSENT}. */
    public LifecycleStatus {
        state = state == null ? LifecycleState.ABSENT : state;
        detail = detail == null ? "" : detail.trim();
    }

    /** {@return a ready status with no detail} */
    public static @NotNull LifecycleStatus ready() {
        return new LifecycleStatus(LifecycleState.READY, "");
    }

    /** {@return an absent status with no detail} */
    public static @NotNull LifecycleStatus absent() {
        return new LifecycleStatus(LifecycleState.ABSENT, "");
    }

    /**
     * Creates a waiting status.
     *
     * @param detail what is still being waited on
     * @return the status
     */
    public static @NotNull LifecycleStatus waiting(@Nullable String detail) {
        return new LifecycleStatus(LifecycleState.WAITING, detail);
    }

    /**
     * Creates an incompatible status.
     *
     * @param detail what failed to link
     * @return the status
     */
    public static @NotNull LifecycleStatus incompatible(@Nullable String detail) {
        return new LifecycleStatus(LifecycleState.INCOMPATIBLE, detail);
    }

    /** {@return whether the provider can resolve items right now} */
    public boolean usable() {
        return state == LifecycleState.READY;
    }
}
