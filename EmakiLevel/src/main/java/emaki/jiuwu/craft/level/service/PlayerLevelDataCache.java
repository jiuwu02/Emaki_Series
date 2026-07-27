package emaki.jiuwu.craft.level.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import emaki.jiuwu.craft.level.model.PlayerLevelData;

final class PlayerLevelDataCache {

    enum Lifecycle {
        LOADING,
        ACTIVE,
        CLOSING,
        LOAD_FAILED
    }

    enum CommitResult {
        COMMITTED,
        STALE,
        REJECTED
    }

    record SessionTicket(UUID playerId, Object entryIdentity, long generation) {
    }

    record SaveTicket(UUID playerId,
            Object entryIdentity,
            long generation,
            long revision,
            PlayerLevelData snapshot,
            boolean closeAfterSave) {

        SaveTicket {
            snapshot = snapshot == null ? null : snapshot.copy();
        }

        @Override
        public PlayerLevelData snapshot() {
            return snapshot == null ? null : snapshot.copy();
        }
    }

    record Snapshot(long generation,
            long revision,
            long persistedRevision,
            Lifecycle lifecycle,
            boolean loadWritable,
            PlayerLevelData data) {

        Snapshot {
            data = data == null ? null : data.copy();
        }

        @Override
        public PlayerLevelData data() {
            return data == null ? null : data.copy();
        }
    }

    private final Map<UUID, SessionEntry> entries = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> generations = new ConcurrentHashMap<>();
    private final Map<UUID, SaveLane> saveLanes = new ConcurrentHashMap<>();
    private final AtomicBoolean sealed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();

    SessionTicket beginSession(UUID playerId, PlayerLevelData initialData, boolean loadWritable) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(initialData, "initialData");
        synchronized (lifecycleLock) {
            if (sealed.get()) {
                return null;
            }
            long generation = generations.computeIfAbsent(playerId, ignored -> new AtomicLong()).incrementAndGet();
            SessionEntry entry = new SessionEntry(playerId, generation, initialData, loadWritable);
            entries.put(playerId, entry);
            return new SessionTicket(playerId, entry.identity, generation);
        }
    }

    boolean seal() {
        synchronized (lifecycleLock) {
            return sealed.compareAndSet(false, true);
        }
    }

    boolean isSealed() {
        return sealed.get();
    }

    CommitResult installLoaded(SessionTicket ticket, PlayerLevelData loaded) {
        if (sealed.get()) {
            return CommitResult.REJECTED;
        }
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return CommitResult.STALE;
        }
        synchronized (entry) {
            if (!matches(entry, ticket) || entry.lifecycle != Lifecycle.LOADING) {
                return CommitResult.STALE;
            }
            entry.data = loaded == null ? entry.data : loaded;
            entry.data.clearDirty();
            entry.loadWritable = true;
            entry.lifecycle = Lifecycle.ACTIVE;
            return CommitResult.COMMITTED;
        }
    }

    CommitResult installLoadFailure(SessionTicket ticket, PlayerLevelData fallback) {
        if (sealed.get()) {
            return CommitResult.REJECTED;
        }
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return CommitResult.STALE;
        }
        synchronized (entry) {
            if (!matches(entry, ticket) || entry.lifecycle != Lifecycle.LOADING) {
                return CommitResult.STALE;
            }
            entry.data = fallback == null ? entry.data : fallback;
            entry.data.clearDirty();
            entry.loadWritable = false;
            entry.lifecycle = Lifecycle.LOAD_FAILED;
            return CommitResult.COMMITTED;
        }
    }

    SessionTicket currentTicket(UUID playerId) {
        SessionEntry entry = entries.get(playerId);
        if (sealed.get() || entry == null) {
            return null;
        }
        synchronized (entry) {
            if (!entry.loadWritable || entry.lifecycle != Lifecycle.ACTIVE) {
                return null;
            }
            return new SessionTicket(playerId, entry.identity, entry.generation);
        }
    }

    long generation(UUID playerId) {
        SessionEntry entry = entries.get(playerId);
        return entry == null ? 0L : entry.generation;
    }

    boolean isKnownGeneration(UUID playerId, long generation) {
        SessionEntry entry = entries.get(playerId);
        return !sealed.get()
                && entry != null
                && entry.generation == generation
                && entry.lifecycle != Lifecycle.CLOSING;
    }

    boolean isCurrentGeneration(UUID playerId, long generation) {
        SessionEntry entry = entries.get(playerId);
        return !sealed.get()
                && entry != null
                && entry.generation == generation
                && entry.loadWritable
                && entry.lifecycle == Lifecycle.ACTIVE;
    }

    Snapshot snapshot(UUID playerId) {
        SessionEntry entry = entries.get(playerId);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            return new Snapshot(
                    entry.generation,
                    entry.data.revision(),
                    entry.data.persistedRevision(),
                    entry.lifecycle,
                    entry.loadWritable,
                    entry.data
            );
        }
    }

    PlayerLevelData activeData(UUID playerId) {
        SessionEntry entry = entries.get(playerId);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            return entry.loadWritable && entry.lifecycle == Lifecycle.ACTIVE ? entry.data.copy() : null;
        }
    }

    <R> R mutate(UUID playerId, long expectedGeneration, Function<PlayerLevelData, R> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (sealed.get()) {
            return null;
        }
        SessionEntry entry = entries.get(playerId);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            if (expectedGeneration > 0L && entry.generation != expectedGeneration) {
                return null;
            }
            if (!entry.loadWritable || entry.lifecycle != Lifecycle.ACTIVE) {
                return null;
            }
            return mutation.apply(entry.data);
        }
    }

    SaveTicket snapshotForSave(UUID playerId, long expectedGeneration, boolean closeAfterSave) {
        SessionEntry entry = entries.get(playerId);
        if (entry == null || expectedGeneration > 0L && entry.generation != expectedGeneration) {
            return null;
        }
        synchronized (entry) {
            if (closeAfterSave) {
                if (entry.lifecycle == Lifecycle.CLOSING && entry.closeTicket != null) {
                    return entry.closeTicket;
                }
                entry.lifecycle = Lifecycle.CLOSING;
            }
            if (!entry.loadWritable) {
                if (closeAfterSave) {
                    entries.remove(playerId, entry);
                }
                return null;
            }
            long revision = entry.data.revision();
            if (revision <= entry.data.persistedRevision()) {
                if (closeAfterSave) {
                    entries.remove(playerId, entry);
                }
                return null;
            }
            SaveTicket ticket = new SaveTicket(
                    playerId,
                    entry.identity,
                    entry.generation,
                    revision,
                    entry.data,
                    closeAfterSave
            );
            if (closeAfterSave) {
                entry.closeTicket = ticket;
            }
            return ticket;
        }
    }

    List<SaveTicket> snapshotDirtyEntries() {
        List<SaveTicket> tickets = new ArrayList<>();
        for (Map.Entry<UUID, SessionEntry> mapped : entries.entrySet()) {
            SessionEntry entry = mapped.getValue();
            boolean closeAfterSave;
            long generation;
            synchronized (entry) {
                closeAfterSave = entry.lifecycle == Lifecycle.CLOSING;
                generation = entry.generation;
            }
            SaveTicket ticket = snapshotForSave(mapped.getKey(), generation, closeAfterSave);
            if (ticket != null) {
                tickets.add(ticket);
            }
        }
        return List.copyOf(tickets);
    }

    CompletableFuture<Boolean> enqueueSave(SaveTicket ticket,
            Function<SaveTicket, CompletableFuture<Boolean>> writer) {
        Objects.requireNonNull(writer, "writer");
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return CompletableFuture.completedFuture(false);
        }
        synchronized (entry) {
            if (ticket.closeAfterSave()
                    && Objects.equals(entry.closeTicket, ticket)
                    && entry.closeFuture != null) {
                return entry.closeFuture;
            }

            SaveLane lane = saveLanes.computeIfAbsent(ticket.playerId(), ignored -> new SaveLane());
            CompletableFuture<Boolean> save;
            synchronized (lane) {
                CompletableFuture<Void> previous = lane.tail;
                save = previous.handle((ignored, throwable) -> null)
                        .thenCompose(ignored -> {
                            CompletableFuture<Boolean> submitted;
                            try {
                                submitted = writer.apply(ticket);
                            } catch (RuntimeException exception) {
                                submitted = CompletableFuture.failedFuture(exception);
                            }
                            return submitted == null ? CompletableFuture.completedFuture(false) : submitted;
                        })
                        .handle((saved, throwable) -> {
                            if (throwable == null && Boolean.TRUE.equals(saved)) {
                                return commitSaved(ticket) == CommitResult.COMMITTED;
                            }
                            commitFailed(ticket);
                            return false;
                        });
                lane.tail = save.handle((ignored, throwable) -> null);
            }
            if (!ticket.closeAfterSave()) {
                return save;
            }
            CompletableFuture<Boolean> closeResult = new CompletableFuture<>();
            entry.closeFuture = closeResult;
            save.whenComplete((saved, throwable) -> {
                if (throwable != null) {
                    closeResult.completeExceptionally(throwable);
                } else {
                    closeResult.complete(Boolean.TRUE.equals(saved));
                }
            });
            return closeResult;
        }
    }

    CompletableFuture<Void> waitForIdle(UUID playerId) {
        SaveLane lane = saveLanes.get(playerId);
        if (lane == null) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (lane) {
            return lane.tail;
        }
    }

    CompletableFuture<Void> waitForIdle() {
        List<CompletableFuture<Void>> tails = new ArrayList<>();
        for (SaveLane lane : saveLanes.values()) {
            synchronized (lane) {
                tails.add(lane.tail);
            }
        }
        return tails.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(tails.toArray(CompletableFuture[]::new));
    }

    Map<UUID, PlayerLevelData> activeDataSnapshot() {
        Map<UUID, PlayerLevelData> snapshot = new LinkedHashMap<>();
        for (Map.Entry<UUID, SessionEntry> mapped : entries.entrySet()) {
            PlayerLevelData data = activeData(mapped.getKey());
            if (data != null) {
                snapshot.put(mapped.getKey(), data);
            }
        }
        return Map.copyOf(snapshot);
    }

    int dirtyCount() {
        int count = 0;
        for (SessionEntry entry : entries.values()) {
            synchronized (entry) {
                if (entry.loadWritable && entry.data.dirty()) {
                    count++;
                }
            }
        }
        return count;
    }

    int size() {
        return entries.size();
    }

    private CommitResult commitSaved(SaveTicket ticket) {
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return CommitResult.STALE;
        }
        boolean remove = false;
        synchronized (entry) {
            if (!matches(entry, ticket)) {
                return CommitResult.STALE;
            }
            entry.data.markPersisted(ticket.revision());
            if (ticket.closeAfterSave()
                    && entry.lifecycle == Lifecycle.CLOSING
                    && entry.data.revision() == ticket.revision()
                    && Objects.equals(entry.closeTicket, ticket)) {
                remove = true;
            }
        }
        if (remove) {
            entries.remove(ticket.playerId(), entry);
        }
        return CommitResult.COMMITTED;
    }

    private CommitResult commitFailed(SaveTicket ticket) {
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return CommitResult.STALE;
        }
        synchronized (entry) {
            if (!matches(entry, ticket)) {
                return CommitResult.STALE;
            }
            if (Objects.equals(entry.closeTicket, ticket)) {
                entry.closeTicket = null;
                entry.closeFuture = null;
            }
            return CommitResult.COMMITTED;
        }
    }

    private SessionEntry currentEntry(SessionTicket ticket) {
        if (ticket == null) {
            return null;
        }
        SessionEntry entry = entries.get(ticket.playerId());
        return entry != null && matches(entry, ticket) ? entry : null;
    }

    private SessionEntry currentEntry(SaveTicket ticket) {
        if (ticket == null) {
            return null;
        }
        SessionEntry entry = entries.get(ticket.playerId());
        return entry != null && matches(entry, ticket) ? entry : null;
    }

    private boolean matches(SessionEntry entry, SessionTicket ticket) {
        return entry != null
                && ticket != null
                && entry.identity == ticket.entryIdentity()
                && entry.generation == ticket.generation();
    }

    private boolean matches(SessionEntry entry, SaveTicket ticket) {
        return entry != null
                && ticket != null
                && entry.identity == ticket.entryIdentity()
                && entry.generation == ticket.generation();
    }

    private static final class SaveLane {

        private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    }

    private static final class SessionEntry {

        private final UUID playerId;
        private final Object identity = new Object();
        private final long generation;
        private PlayerLevelData data;
        private boolean loadWritable;
        private Lifecycle lifecycle = Lifecycle.LOADING;
        private SaveTicket closeTicket;
        private CompletableFuture<Boolean> closeFuture;

        private SessionEntry(UUID playerId, long generation, PlayerLevelData data, boolean loadWritable) {
            this.playerId = playerId;
            this.generation = generation;
            this.data = data;
            this.loadWritable = loadWritable;
        }
    }
}
