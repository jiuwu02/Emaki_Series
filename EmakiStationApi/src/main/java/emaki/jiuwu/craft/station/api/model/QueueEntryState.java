package emaki.jiuwu.craft.station.api.model;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lifecycle state of one queued craft.
 *
 * <p>Only {@link #WAITING} and {@link #RUNNING} occupy queue length. {@link #PENDING_CLAIM} is
 * deliberately excluded from that budget: a player whose warehouse and inventory are both full would
 * otherwise be deadlocked by their own undelivered output and unable to queue anything else. Pending
 * claims are bounded by their own separate ceiling instead.
 */
public enum QueueEntryState {

    /** Materials are already consumed, but the entry is not at the head of the queue yet. */
    WAITING,

    /** The entry is at the head of the queue and its duration is being counted down. */
    RUNNING,

    /** The craft finished but the outputs could not be delivered, so they wait for a manual claim. */
    PENDING_CLAIM;

    /** {@return the lower-case persisted token for this state} */
    public @NotNull String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** {@return whether this state consumes one unit of the station's queue length} */
    public boolean occupiesQueueLength() {
        return this != PENDING_CLAIM;
    }

    /**
     * Parses a persisted token, falling back when the value is absent or unknown.
     *
     * @param raw      the persisted token; {@code null} and blank are treated as absent
     * @param fallback the value to use when {@code raw} does not name a state
     * @return the parsed state, or {@code fallback}
     */
    public static @NotNull QueueEntryState parse(@Nullable String raw, @NotNull QueueEntryState fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (QueueEntryState state : values()) {
            if (state.token().equals(normalized)) {
                return state;
            }
        }
        return fallback;
    }
}
