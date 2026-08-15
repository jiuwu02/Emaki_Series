package emaki.jiuwu.craft.storage.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import emaki.jiuwu.craft.storage.model.PlayerStorage;

public final class PlayerStorageCache {

    public enum Lifecycle {
        LOADING,
        ACTIVE,
        CLOSING,
        LOAD_FAILED
    }

    public enum CommitResult {
        COMMITTED,

        STALE,

        REJECTED
    }

    public record SessionTicket(UUID playerId, Object entryIdentity, long generation) {
    }

    public record SaveTicket(UUID playerId,
            Object entryIdentity,
            long generation,
            long revision,
            PlayerStorage snapshot,
            boolean closeAfterSave) {
    }

    private static final class SaveLane {
        private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    }

    private static final class SessionEntry {
        private final UUID playerId;
        private final Object identity = new Object();
        private final long generation;
        private PlayerStorage data;
        private boolean writable;
        private Lifecycle lifecycle = Lifecycle.LOADING;
        private SaveTicket closeTicket;

        private SessionEntry(UUID playerId, long generation, PlayerStorage data, boolean writable) {
            this.playerId = playerId;
            this.generation = generation;
            this.data = data;
            this.writable = writable;
        }
    }

    private final Map<UUID, SessionEntry> entries = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> generations = new ConcurrentHashMap<>();
    private final Map<UUID, SaveLane> saveLanes = new ConcurrentHashMap<>();
    private final AtomicBoolean sealed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();

    public SessionTicket beginSession(UUID playerId, PlayerStorage initialData, boolean writable) {
        if (sealed.get()) {
            return null;
        }
        synchronized (lifecycleLock) {
            if (sealed.get()) {
                return null;
            }
            long generation = generations.computeIfAbsent(playerId, id -> new AtomicLong())
                    .incrementAndGet();
            SessionEntry entry = new SessionEntry(playerId, generation, initialData, writable);
            entries.put(playerId, entry);
            return new SessionTicket(playerId, entry.identity, generation);
        }
    }

    public boolean seal() {
        return sealed.compareAndSet(false, true);
    }

    public boolean isSealed() {
        return sealed.get();
    }

    public CommitResult installLoaded(SessionTicket ticket, PlayerStorage loaded) {
        synchronized (lifecycleLock) {
            SessionEntry entry = matching(ticket);
            if (entry == null) {
                return sealed.get() ? CommitResult.REJECTED : CommitResult.STALE;
            }
            loaded.clearDirty();
            entry.data = loaded;
            entry.writable = true;
            entry.lifecycle = Lifecycle.ACTIVE;
            return CommitResult.COMMITTED;
        }
    }

    public CommitResult installLoadFailure(SessionTicket ticket, PlayerStorage fallback) {
        synchronized (lifecycleLock) {
            SessionEntry entry = matching(ticket);
            if (entry == null) {
                return sealed.get() ? CommitResult.REJECTED : CommitResult.STALE;
            }
            entry.data = fallback;
            entry.writable = false;
            entry.lifecycle = Lifecycle.LOAD_FAILED;
            return CommitResult.COMMITTED;
        }
    }

    public PlayerStorage active(UUID playerId) {
        SessionEntry entry = entries.get(playerId);
        if (entry == null || entry.lifecycle == Lifecycle.LOADING) {
            return null;
        }
        return entry.data;
    }

    public long generation(UUID playerId) {
        AtomicLong counter = generations.get(playerId);
        return counter == null ? 0L : counter.get();
    }

    public boolean isCurrentGeneration(UUID playerId, long generation) {
        SessionEntry entry = entries.get(playerId);
        return entry != null && entry.generation == generation;
    }

    public boolean writable(UUID playerId) {
        SessionEntry entry = entries.get(playerId);
        return entry != null && entry.writable && entry.lifecycle != Lifecycle.LOADING;
    }

    public Map<UUID, PlayerStorage> activeSnapshot() {
        Map<UUID, PlayerStorage> snapshot = new ConcurrentHashMap<>();
        entries.forEach((playerId, entry) -> {
            if (entry.data != null && entry.lifecycle != Lifecycle.LOADING) {
                snapshot.put(playerId, entry.data);
            }
        });
        return Map.copyOf(snapshot);
    }

    public SaveTicket snapshotForSave(UUID playerId, boolean closeAfterSave) {
        synchronized (lifecycleLock) {
            SessionEntry entry = entries.get(playerId);
            if (entry == null) {
                return null;
            }
            if (closeAfterSave) {
                if (entry.closeTicket != null) {
                    return entry.closeTicket;
                }
                entry.lifecycle = Lifecycle.CLOSING;
            }
            if (!entry.writable || entry.data == null || !entry.data.dirty()) {
                if (closeAfterSave) {
                    entries.remove(playerId, entry);
                }
                return null;
            }
            SaveTicket ticket = new SaveTicket(playerId, entry.identity, entry.generation,
                    entry.data.revision(), entry.data.copy(), closeAfterSave);
            if (closeAfterSave) {
                entry.closeTicket = ticket;
            }
            return ticket;
        }
    }

    public List<SaveTicket> snapshotDirtyEntries() {
        List<SaveTicket> tickets = new ArrayList<>();
        for (UUID playerId : List.copyOf(entries.keySet())) {
            SaveTicket ticket = snapshotForSave(playerId, false);
            if (ticket != null) {
                tickets.add(ticket);
            }
        }
        return tickets;
    }

    public CompletableFuture<Boolean> enqueueSaveAsync(SaveTicket ticket,
            Function<SaveTicket, CompletableFuture<Boolean>> writer) {
        SaveLane lane = saveLanes.computeIfAbsent(ticket.playerId(), id -> new SaveLane());
        CompletableFuture<Boolean> save;
        synchronized (lane) {
            CompletableFuture<Void> previous = lane.tail;
            save = previous.handle((ignored, throwable) -> null)
                    .thenCompose(ignored -> {
                        CompletableFuture<Boolean> submitted;
                        try {
                            submitted = writer.apply(ticket);
                        } catch (RuntimeException failure) {
                            submitted = CompletableFuture.failedFuture(failure);
                        }
                        return submitted == null ? CompletableFuture.completedFuture(false) : submitted;
                    })
                    .handle((saved, throwable) -> {
                        boolean success = throwable == null && Boolean.TRUE.equals(saved);
                        if (success) {
                            commitSaved(ticket);
                        } else {
                            commitFailed(ticket);
                        }
                        return success;
                    });
            lane.tail = save.handle((ignored, throwable) -> null);
        }
        return save;
    }

    public CompletableFuture<Void> waitForIdleAsync(UUID playerId) {
        SaveLane lane = saveLanes.get(playerId);
        if (lane == null) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (lane) {
            return lane.tail.handle((ignored, throwable) -> null);
        }
    }

    public void discard(UUID playerId) {
        synchronized (lifecycleLock) {
            entries.remove(playerId);
        }
    }

    public int dirtyCount() {
        int dirty = 0;
        for (SessionEntry entry : entries.values()) {
            if (entry.writable && entry.data != null && entry.data.dirty()) {
                dirty++;
            }
        }
        return dirty;
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        synchronized (lifecycleLock) {
            entries.clear();
            saveLanes.clear();
        }
    }

    private void commitSaved(SaveTicket ticket) {
        synchronized (lifecycleLock) {
            SessionEntry entry = entries.get(ticket.playerId());
            if (entry == null || entry.identity != ticket.entryIdentity()
                    || entry.generation != ticket.generation()) {
                return;
            }
            if (entry.data != null) {
                entry.data.markPersisted(ticket.revision());
            }
            if (ticket.closeAfterSave()) {
                entries.remove(ticket.playerId(), entry);
            }
        }
    }

    private void commitFailed(SaveTicket ticket) {
        synchronized (lifecycleLock) {
            SessionEntry entry = entries.get(ticket.playerId());
            if (entry != null && entry.identity == ticket.entryIdentity()) {
                entry.closeTicket = null;
            }
        }
    }

    private SessionEntry matching(SessionTicket ticket) {
        if (ticket == null) {
            return null;
        }
        SessionEntry entry = entries.get(ticket.playerId());
        if (entry == null || entry.identity != ticket.entryIdentity()
                || entry.generation != ticket.generation()) {
            return null;
        }
        return entry;
    }
}
