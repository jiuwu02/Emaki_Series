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

/**
 * Session-aware cache for loaded player storages.
 *
 * <p>Mirrors the generation/dirty/save-lane design already used for player level data rather than
 * inventing a second scheme. Three mechanisms do the real work:
 *
 * <ul>
 *   <li><strong>session generation</strong> — every {@code beginSession} bumps a per-player
 *       counter, so an async load that finishes after the player reconnected is discarded instead
 *       of overwriting the newer session. This is also what invalidates an admin's temporary
 *       offline session the moment the target player logs in.</li>
 *   <li><strong>dirty revision</strong> — a save is skipped entirely when nothing changed.</li>
 *   <li><strong>save lane</strong> — writes for one player are chained, so a save never overtakes
 *       an earlier save or a pending load of the same file.</li>
 * </ul>
 */
public final class PlayerStorageCache {

    /** Lifecycle of one cached session. */
    public enum Lifecycle {
        LOADING,
        ACTIVE,
        CLOSING,
        LOAD_FAILED
    }

    /** Result of installing data into a session. */
    public enum CommitResult {
        COMMITTED,
        /** The session was replaced by a newer one; the value was discarded. */
        STALE,
        /** The cache is sealed and no longer accepts sessions. */
        REJECTED
    }

    /** Opaque handle proving which session an async load belongs to. */
    public record SessionTicket(UUID playerId, Object entryIdentity, long generation) {
    }

    /** Snapshot handed to the async writer. */
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

    /**
     * Opens a new session, superseding any existing one for that player.
     *
     * @param playerId    the storage owner
     * @param initialData the placeholder used until the real data arrives
     * @param writable    whether this session may be written back to disk
     * @return a ticket to pass to {@link #installLoaded}, or {@code null} when sealed
     */
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

    /** Seals the cache so no new session or write is accepted. */
    public boolean seal() {
        return sealed.compareAndSet(false, true);
    }

    public boolean isSealed() {
        return sealed.get();
    }

    /**
     * Installs successfully loaded data.
     *
     * @param ticket the ticket returned by {@link #beginSession}
     * @param loaded the data read from disk
     * @return whether the data was accepted
     */
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

    /**
     * Marks a session read-only after a failed load.
     *
     * <p>The session stays usable for reads but is never written back, so a transient read error
     * cannot overwrite a healthy file on disk with defaults.
     *
     * @param ticket   the ticket returned by {@link #beginSession}
     * @param fallback the empty storage to expose
     * @return whether the fallback was accepted
     */
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

    /** {@return the live storage for an active session, or {@code null} when not usable} */
    public PlayerStorage active(UUID playerId) {
        SessionEntry entry = entries.get(playerId);
        if (entry == null || entry.lifecycle == Lifecycle.LOADING) {
            return null;
        }
        return entry.data;
    }

    /** {@return the current generation, or {@code 0} when no session exists} */
    public long generation(UUID playerId) {
        AtomicLong counter = generations.get(playerId);
        return counter == null ? 0L : counter.get();
    }

    /** {@return whether the supplied generation is still the live one} */
    public boolean isCurrentGeneration(UUID playerId, long generation) {
        SessionEntry entry = entries.get(playerId);
        return entry != null && entry.generation == generation;
    }

    /** {@return whether the session may be written back to disk} */
    public boolean writable(UUID playerId) {
        SessionEntry entry = entries.get(playerId);
        return entry != null && entry.writable && entry.lifecycle != Lifecycle.LOADING;
    }

    /** {@return every currently cached storage} */
    public Map<UUID, PlayerStorage> activeSnapshot() {
        Map<UUID, PlayerStorage> snapshot = new ConcurrentHashMap<>();
        entries.forEach((playerId, entry) -> {
            if (entry.data != null && entry.lifecycle != Lifecycle.LOADING) {
                snapshot.put(playerId, entry.data);
            }
        });
        return Map.copyOf(snapshot);
    }

    /**
     * Builds a save ticket, or {@code null} when nothing needs writing.
     *
     * @param playerId       the storage owner
     * @param closeAfterSave whether the entry should leave the cache once written
     * @return the ticket, or {@code null} when the session is unwritable or clean
     */
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

    /** {@return save tickets for every dirty session, used during shutdown} */
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

    /**
     * Queues a write on the player's serial lane.
     *
     * @param ticket the save ticket
     * @param writer performs the actual IO and reports success
     * @return a future completing with whether the write succeeded
     */
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

    /** {@return a future settling once the player's lane is idle} */
    public CompletableFuture<Void> waitForIdleAsync(UUID playerId) {
        SaveLane lane = saveLanes.get(playerId);
        if (lane == null) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (lane) {
            return lane.tail.handle((ignored, throwable) -> null);
        }
    }

    /** Drops a session outright, used when a load was superseded before it completed. */
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

    /** Clears every session; only valid after {@link #seal()} and a completed drain. */
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
