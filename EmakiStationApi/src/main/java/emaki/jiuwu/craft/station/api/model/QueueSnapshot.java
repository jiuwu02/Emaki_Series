package emaki.jiuwu.craft.station.api.model;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of one player's queue at one station.
 *
 * @param playerId    the queue owner
 * @param stationId   the station this queue belongs to
 * @param entries     the queued entries in position order
 * @param maxLength   the effective queue length ceiling for this owner at this station
 * @param progressMode how the head entry advances
 */
public record QueueSnapshot(@NotNull UUID playerId,
        @NotNull String stationId,
        @NotNull List<QueueEntryView> entries,
        int maxLength,
        @NotNull ProgressMode progressMode) {

    /**
     * Creates a queue snapshot with a defensively copied entry list.
     *
     * @param playerId    the queue owner
     * @param stationId   the station this queue belongs to
     * @param entries     the queued entries; {@code null} becomes empty
     * @param maxLength   the effective queue length ceiling
     * @param progressMode how the head entry advances
     * @throws NullPointerException when {@code playerId}, {@code stationId}, or {@code progressMode}
     *         is {@code null}
     */
    public QueueSnapshot {
        if (playerId == null) {
            throw new NullPointerException("playerId");
        }
        if (stationId == null) {
            throw new NullPointerException("stationId");
        }
        if (progressMode == null) {
            throw new NullPointerException("progressMode");
        }
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** {@return how many entries count against {@link #maxLength()}} */
    public int occupiedLength() {
        int occupied = 0;
        for (QueueEntryView entry : entries) {
            if (entry.state().occupiesQueueLength()) {
                occupied++;
            }
        }
        return occupied;
    }

    /** {@return how many entries are waiting to be claimed} */
    public int pendingClaimCount() {
        int pending = 0;
        for (QueueEntryView entry : entries) {
            if (entry.awaitingClaim()) {
                pending++;
            }
        }
        return pending;
    }
}
