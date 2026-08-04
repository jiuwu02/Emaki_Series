package emaki.jiuwu.craft.station.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import emaki.jiuwu.craft.station.api.model.ProgressMode;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;
import emaki.jiuwu.craft.station.api.model.QueueEntryView;
import emaki.jiuwu.craft.station.api.model.QueueSnapshot;

/**
 * One player's queue at one station.
 *
 * <p><strong>Serial, single-line.</strong> Only the first entry that still occupies queue length ever
 * advances; everything behind it sits at zero progress. This is what makes cancellation refunds simple
 * enough to be worth having: at most one entry has partial progress, so there is no per-entry progress
 * accounting to reconcile.
 *
 * <p>Pending-claim entries stay in the list but are skipped when picking the head and excluded from the
 * length budget, so undelivered output can never deadlock a player's queue.
 *
 * <p>Not thread-safe on its own; {@link QueueService} confines each queue's mutations to its owner's owner
 * thread.
 */
public final class CraftQueue {

    private final UUID playerId;
    private final String stationId;
    private final List<QueueEntry> entries = new ArrayList<>();

    /**
     * Creates an empty queue.
     *
     * @param playerId  the queue owner
     * @param stationId the station this queue belongs to
     */
    public CraftQueue(UUID playerId, String stationId) {
        this.playerId = playerId;
        this.stationId = stationId;
    }

    /** {@return the queue owner} */
    public UUID playerId() {
        return playerId;
    }

    /** {@return the station this queue belongs to} */
    public String stationId() {
        return stationId;
    }

    /** {@return the entries in position order; a live view} */
    public List<QueueEntry> entries() {
        return entries;
    }

    /** {@return whether this queue holds no entries at all} */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Appends an entry.
     *
     * @param entry the entry to add
     * @return the position it took
     */
    public int add(QueueEntry entry) {
        entries.add(entry);
        return entries.size() - 1;
    }

    /**
     * Removes an entry by position.
     *
     * @param index the zero-based position
     * @return the removed entry, or {@code null} when the position does not exist
     */
    public QueueEntry remove(int index) {
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.remove(index);
    }

    /**
     * Reads an entry by position.
     *
     * @param index the zero-based position
     * @return the entry, or {@code null} when the position does not exist
     */
    public QueueEntry at(int index) {
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    /** {@return how many entries count against the length ceiling} */
    public int occupiedLength() {
        int occupied = 0;
        for (QueueEntry entry : entries) {
            if (entry.state().occupiesQueueLength()) {
                occupied++;
            }
        }
        return occupied;
    }

    /** {@return how many entries are waiting to be claimed} */
    public int pendingClaimCount() {
        int pending = 0;
        for (QueueEntry entry : entries) {
            if (entry.state() == QueueEntryState.PENDING_CLAIM) {
                pending++;
            }
        }
        return pending;
    }

    /**
     * {@return the entry that should be advancing, or {@code null} when there is none}
     *
     * <p>Prefers an already-running entry so a restart cannot promote a second one, then falls back to the
     * first waiting entry.
     */
    public QueueEntry head() {
        for (QueueEntry entry : entries) {
            if (entry.state() == QueueEntryState.RUNNING) {
                return entry;
            }
        }
        for (QueueEntry entry : entries) {
            if (entry.state() == QueueEntryState.WAITING) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Promotes the first waiting entry when nothing is running.
     *
     * @param mode the station's progress mode
     * @param now  the current wall-clock time
     * @return the entry now running, or {@code null} when nothing could start
     */
    public QueueEntry promoteHead(ProgressMode mode, long now) {
        QueueEntry current = head();
        if (current == null) {
            return null;
        }
        if (current.state() == QueueEntryState.WAITING) {
            current.start(mode, now);
        }
        return current.state() == QueueEntryState.RUNNING ? current : null;
    }

    /**
     * Folds elapsed online progress into every running entry and freezes their clocks.
     *
     * @param now the current wall-clock time
     */
    public void freezeAll(long now) {
        for (QueueEntry entry : entries) {
            entry.freezeOnlineProgress(now);
        }
    }

    /**
     * Resumes online progress for the running entry.
     *
     * @param now the current wall-clock time
     */
    public void resumeAll(long now) {
        for (QueueEntry entry : entries) {
            entry.resumeOnlineProgress(now);
        }
    }

    /**
     * Builds a detached snapshot.
     *
     * @param mode      the station's progress mode
     * @param maxLength the effective length ceiling for this owner
     * @param now       the current wall-clock time
     * @return the snapshot
     */
    public QueueSnapshot toSnapshot(ProgressMode mode, int maxLength, long now) {
        List<QueueEntryView> views = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            views.add(entries.get(index).toView(index, mode, now));
        }
        return new QueueSnapshot(playerId, stationId, views, maxLength, mode);
    }
}
