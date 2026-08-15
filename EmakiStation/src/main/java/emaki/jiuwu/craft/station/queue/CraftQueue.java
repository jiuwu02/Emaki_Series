package emaki.jiuwu.craft.station.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import emaki.jiuwu.craft.station.api.model.ProgressMode;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;
import emaki.jiuwu.craft.station.api.model.QueueEntryView;
import emaki.jiuwu.craft.station.api.model.QueueSnapshot;

public final class CraftQueue {

    private final UUID playerId;
    private final String stationId;
    private final List<QueueEntry> entries = new ArrayList<>();

    public CraftQueue(UUID playerId, String stationId) {
        this.playerId = playerId;
        this.stationId = stationId;
    }

    public UUID playerId() {
        return playerId;
    }

    public String stationId() {
        return stationId;
    }

    public List<QueueEntry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int add(QueueEntry entry) {
        entries.add(entry);
        return entries.size() - 1;
    }

    public QueueEntry remove(int index) {
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.remove(index);
    }

    public QueueEntry at(int index) {
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    public int occupiedLength() {
        int occupied = 0;
        for (QueueEntry entry : entries) {
            if (entry.state().occupiesQueueLength()) {
                occupied++;
            }
        }
        return occupied;
    }

    public int pendingClaimCount() {
        int pending = 0;
        for (QueueEntry entry : entries) {
            if (entry.state() == QueueEntryState.PENDING_CLAIM) {
                pending++;
            }
        }
        return pending;
    }

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

    public void freezeAll(long now) {
        for (QueueEntry entry : entries) {
            entry.freezeOnlineProgress(now);
        }
    }

    public void resumeAll(long now) {
        for (QueueEntry entry : entries) {
            entry.resumeOnlineProgress(now);
        }
    }

    public QueueSnapshot toSnapshot(ProgressMode mode, int maxLength, long now) {
        List<QueueEntryView> views = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            views.add(entries.get(index).toView(index, mode, now));
        }
        return new QueueSnapshot(playerId, stationId, views, maxLength, mode);
    }
}
