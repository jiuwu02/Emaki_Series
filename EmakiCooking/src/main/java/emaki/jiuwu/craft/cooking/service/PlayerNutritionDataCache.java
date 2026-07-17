package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import emaki.jiuwu.craft.cooking.model.PlayerNutritionData;

/**
 * Session-aware nutrition cache with generation and revision CAS checks.
 */
final class PlayerNutritionDataCache {

    enum SessionState {
        LOADING,
        ACTIVE,
        FAILED,
        CLOSING,
        CLOSED
    }

    enum CommitResult {
        COMMITTED,
        STALE,
        SEALED
    }

    record SessionTicket(UUID uuid, long generation, PlayerNutritionData fallback) {
    }

    record SaveTicket(UUID uuid, long generation, long revision, PlayerNutritionData snapshot,
            boolean closeAfterSave) {
    }

    record Snapshot(UUID uuid, long generation, SessionState state, long revision, long persistedRevision,
            PlayerNutritionData data) {
    }

    private static final class SessionEntry {

        private final UUID uuid;
        private final long generation;
        private final Object lock = new Object();
        private SessionState state = SessionState.LOADING;
        private PlayerNutritionData data;
        private PlayerNutritionData fallback;

        private SessionEntry(UUID uuid, long generation, PlayerNutritionData fallback) {
            this.uuid = uuid;
            this.generation = generation;
            this.fallback = fallback;
        }
    }

    private final AtomicLong generationSequence = new AtomicLong();
    private final ConcurrentMap<UUID, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<Void>> saveLanes = new ConcurrentHashMap<>();
    private final Object laneLock = new Object();
    private final AtomicBoolean sealed = new AtomicBoolean(false);

    SessionTicket beginSession(UUID uuid, PlayerNutritionData fallback, boolean retryFailed) {
        if (uuid == null || sealed.get()) {
            return null;
        }
        SessionEntry[] selected = new SessionEntry[1];
        sessions.compute(uuid, (_, current) -> {
            if (current != null) {
                synchronized (current.lock) {
                    if (current.state == SessionState.ACTIVE
                            || (!retryFailed && current.state == SessionState.LOADING)
                            || (!retryFailed && current.state == SessionState.FAILED)) {
                        selected[0] = current;
                        return current;
                    }
                }
            }
            SessionEntry created = new SessionEntry(uuid, generationSequence.incrementAndGet(), fallback);
            selected[0] = created;
            return created;
        });
        SessionEntry entry = selected[0];
        return entry == null ? null : new SessionTicket(entry.uuid, entry.generation, copy(entry.fallback));
    }

    CommitResult installLoaded(SessionTicket ticket, PlayerNutritionData loaded) {
        if (ticket == null || loaded == null) {
            return CommitResult.STALE;
        }
        SessionEntry entry = sessions.get(ticket.uuid());
        if (entry == null || entry.generation != ticket.generation()) {
            return CommitResult.STALE;
        }
        synchronized (entry.lock) {
            if (sessions.get(ticket.uuid()) != entry || entry.state != SessionState.LOADING) {
                return CommitResult.STALE;
            }
            if (sealed.get()) {
                entry.state = SessionState.CLOSED;
                sessions.remove(ticket.uuid(), entry);
                return CommitResult.SEALED;
            }
            loaded.clearDirty();
            entry.data = copy(loaded);
            entry.fallback = null;
            entry.state = SessionState.ACTIVE;
            return CommitResult.COMMITTED;
        }
    }

    CommitResult installLoadFailure(SessionTicket ticket, PlayerNutritionData readOnlyFallback) {
        if (ticket == null) {
            return CommitResult.STALE;
        }
        SessionEntry entry = sessions.get(ticket.uuid());
        if (entry == null || entry.generation != ticket.generation()) {
            return CommitResult.STALE;
        }
        synchronized (entry.lock) {
            if (sessions.get(ticket.uuid()) != entry || entry.state != SessionState.LOADING) {
                return CommitResult.STALE;
            }
            entry.fallback = copy(readOnlyFallback != null ? readOnlyFallback : ticket.fallback());
            entry.state = SessionState.FAILED;
            return CommitResult.COMMITTED;
        }
    }

    PlayerNutritionData activeData(UUID uuid) {
        SessionEntry entry = sessions.get(uuid);
        if (entry == null) {
            return null;
        }
        synchronized (entry.lock) {
            if (sessions.get(uuid) != entry || entry.state != SessionState.ACTIVE || entry.data == null) {
                return null;
            }
            return copy(entry.data);
        }
    }

    PlayerNutritionData visibleData(UUID uuid) {
        SessionEntry entry = sessions.get(uuid);
        if (entry == null) {
            return null;
        }
        synchronized (entry.lock) {
            if (sessions.get(uuid) != entry) {
                return null;
            }
            if (entry.state == SessionState.ACTIVE && entry.data != null) {
                return copy(entry.data);
            }
            if (entry.state == SessionState.FAILED) {
                return copy(entry.fallback);
            }
            return null;
        }
    }

    <T> T mutate(UUID uuid, long generation, Function<PlayerNutritionData, T> mutation) {
        if (uuid == null || mutation == null || sealed.get()) {
            return null;
        }
        SessionEntry entry = sessions.get(uuid);
        if (entry == null || entry.generation != generation) {
            return null;
        }
        synchronized (entry.lock) {
            if (sealed.get()
                    || sessions.get(uuid) != entry
                    || entry.state != SessionState.ACTIVE
                    || entry.data == null) {
                return null;
            }
            return mutation.apply(entry.data);
        }
    }

    SessionTicket currentTicket(UUID uuid) {
        SessionEntry entry = sessions.get(uuid);
        if (entry == null) {
            return null;
        }
        synchronized (entry.lock) {
            if (sessions.get(uuid) != entry || entry.state != SessionState.ACTIVE) {
                return null;
            }
            return new SessionTicket(uuid, entry.generation, null);
        }
    }

    long generation(UUID uuid) {
        SessionEntry entry = sessions.get(uuid);
        return entry == null ? -1L : entry.generation;
    }

    boolean isCurrentGeneration(UUID uuid, long generation) {
        if (sealed.get()) {
            return false;
        }
        SessionEntry entry = sessions.get(uuid);
        if (entry == null || entry.generation != generation) {
            return false;
        }
        synchronized (entry.lock) {
            return sessions.get(uuid) == entry
                    && (entry.state == SessionState.LOADING || entry.state == SessionState.ACTIVE);
        }
    }

    boolean isKnownGeneration(UUID uuid, long generation) {
        SessionEntry entry = sessions.get(uuid);
        return entry != null && entry.generation == generation && sessions.get(uuid) == entry;
    }

    boolean isActiveGeneration(UUID uuid, long generation) {
        SessionEntry entry = sessions.get(uuid);
        if (entry == null || entry.generation != generation) {
            return false;
        }
        synchronized (entry.lock) {
            return sessions.get(uuid) == entry && entry.state == SessionState.ACTIVE;
        }
    }

    SaveTicket snapshotForSave(UUID uuid, long generation, boolean closeAfterSave) {
        SessionEntry entry = sessions.get(uuid);
        if (entry == null || entry.generation != generation) {
            return null;
        }
        synchronized (entry.lock) {
            if (sessions.get(uuid) != entry) {
                return null;
            }
            if (entry.state == SessionState.LOADING || entry.state == SessionState.FAILED) {
                if (closeAfterSave) {
                    entry.state = SessionState.CLOSED;
                    sessions.remove(uuid, entry);
                }
                return null;
            }
            if (entry.state != SessionState.ACTIVE || entry.data == null) {
                return null;
            }
            if (closeAfterSave) {
                entry.state = SessionState.CLOSING;
            }
            long revision = entry.data.revision();
            if (revision <= entry.data.persistedRevision()) {
                if (closeAfterSave) {
                    entry.state = SessionState.CLOSED;
                    sessions.remove(uuid, entry);
                }
                return null;
            }
            return new SaveTicket(uuid, generation, revision, copy(entry.data), closeAfterSave);
        }
    }

    boolean commitSaved(SaveTicket ticket) {
        SessionEntry entry = sessions.get(ticket.uuid());
        if (entry == null || entry.generation != ticket.generation()) {
            return false;
        }
        synchronized (entry.lock) {
            if (sessions.get(ticket.uuid()) != entry || entry.data == null) {
                return false;
            }
            entry.data.markPersisted(ticket.revision());
            if (ticket.closeAfterSave()) {
                if (entry.data.revision() <= ticket.revision()) {
                    entry.state = SessionState.CLOSED;
                    sessions.remove(ticket.uuid(), entry);
                } else if (entry.state == SessionState.CLOSING) {
                    entry.state = SessionState.ACTIVE;
                }
            }
            return true;
        }
    }

    void commitSaveFailure(SaveTicket ticket) {
        SessionEntry entry = sessions.get(ticket.uuid());
        if (entry == null || entry.generation != ticket.generation()) {
            return;
        }
        synchronized (entry.lock) {
            if (sessions.get(ticket.uuid()) == entry && entry.state == SessionState.CLOSING) {
                entry.state = SessionState.ACTIVE;
            }
        }
    }

    boolean discard(UUID uuid, long generation) {
        SessionEntry entry = sessions.get(uuid);
        if (entry == null || entry.generation != generation) {
            return false;
        }
        synchronized (entry.lock) {
            if (sessions.get(uuid) != entry) {
                return false;
            }
            entry.state = SessionState.CLOSED;
            return sessions.remove(uuid, entry);
        }
    }

    CompletableFuture<Boolean> enqueueSave(SaveTicket ticket,
            Function<SaveTicket, CompletableFuture<Boolean>> saveOperation) {
        if (ticket == null || saveOperation == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        synchronized (laneLock) {
            CompletableFuture<Void> previous = saveLanes.get(ticket.uuid());
            CompletableFuture<Void> ready = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((_, _) -> null);
            CompletableFuture<Void> tail = ready.thenCompose(_ -> invokeSave(saveOperation, ticket))
                    .handle((saved, throwable) -> {
                        if (throwable == null && Boolean.TRUE.equals(saved)) {
                            result.complete(commitSaved(ticket));
                        } else {
                            commitSaveFailure(ticket);
                            if (throwable != null) {
                                result.completeExceptionally(throwable);
                            } else {
                                result.complete(false);
                            }
                        }
                        return (Void) null;
                    });
            saveLanes.put(ticket.uuid(), tail);
            tail.whenComplete((_, _) -> {
                synchronized (laneLock) {
                    saveLanes.remove(ticket.uuid(), tail);
                }
            });
        }
        return result;
    }

    CompletableFuture<Void> waitForLane(UUID uuid) {
        synchronized (laneLock) {
            CompletableFuture<Void> tail = saveLanes.get(uuid);
            return tail == null
                    ? CompletableFuture.completedFuture(null)
                    : tail.handle((_, _) -> null);
        }
    }

    private CompletableFuture<Boolean> invokeSave(
            Function<SaveTicket, CompletableFuture<Boolean>> saveOperation,
            SaveTicket ticket) {
        try {
            CompletableFuture<Boolean> future = saveOperation.apply(ticket);
            return future == null ? CompletableFuture.completedFuture(false) : future;
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    List<SaveTicket> dirtySnapshots() {
        List<SaveTicket> tickets = new ArrayList<>();
        for (SessionEntry entry : sessions.values()) {
            synchronized (entry.lock) {
                if (sessions.get(entry.uuid) != entry || entry.data == null) {
                    continue;
                }
                if ((entry.state == SessionState.ACTIVE || entry.state == SessionState.CLOSING)
                        && entry.data.dirty()) {
                    tickets.add(new SaveTicket(
                            entry.uuid,
                            entry.generation,
                            entry.data.revision(),
                            copy(entry.data),
                            false
                    ));
                }
            }
        }
        return tickets;
    }

    List<Snapshot> snapshots() {
        List<Snapshot> snapshots = new ArrayList<>();
        for (SessionEntry entry : sessions.values()) {
            synchronized (entry.lock) {
                if (sessions.get(entry.uuid) != entry) {
                    continue;
                }
                PlayerNutritionData data = entry.data != null ? entry.data : entry.fallback;
                long revision = data == null ? 0L : data.revision();
                long persistedRevision = data == null ? 0L : data.persistedRevision();
                snapshots.add(new Snapshot(
                        entry.uuid,
                        entry.generation,
                        entry.state,
                        revision,
                        persistedRevision,
                        copy(data)
                ));
            }
        }
        return List.copyOf(snapshots);
    }

    Snapshot snapshot(UUID uuid) {
        SessionEntry entry = sessions.get(uuid);
        if (entry == null) {
            return null;
        }
        synchronized (entry.lock) {
            if (sessions.get(uuid) != entry) {
                return null;
            }
            PlayerNutritionData data = entry.data != null ? entry.data : entry.fallback;
            return new Snapshot(
                    uuid,
                    entry.generation,
                    entry.state,
                    data == null ? 0L : data.revision(),
                    data == null ? 0L : data.persistedRevision(),
                    copy(data)
            );
        }
    }

    int size() {
        return sessions.size();
    }

    int dirtyCount() {
        int count = 0;
        for (SessionEntry entry : sessions.values()) {
            synchronized (entry.lock) {
                if (sessions.get(entry.uuid) == entry
                        && entry.data != null
                        && entry.data.dirty()) {
                    count++;
                }
            }
        }
        return count;
    }

    boolean seal() {
        return sealed.compareAndSet(false, true);
    }

    boolean sealed() {
        return sealed.get();
    }

    private PlayerNutritionData copy(PlayerNutritionData data) {
        return data == null ? null : data.copy();
    }
}
