package emaki.jiuwu.craft.corelib.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.api.diagnostics.Anchors;

public abstract class AbstractPlayerSessionCache<K, T extends SessionData<T>> {

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

    public record SessionTicket<K>(K key, Object entryIdentity, long generation) {
    }

    public record SaveTicket<K, T extends SessionData<T>>(K key,
            Object entryIdentity,
            long generation,
            long revision,
            T snapshot,
            boolean closeAfterSave) {

        public SaveTicket {
            snapshot = snapshot == null ? null : snapshot.copy();
        }

        @Override
        public T snapshot() {
            return snapshot == null ? null : snapshot.copy();
        }
    }

    public record Snapshot<T extends SessionData<T>>(long generation,
            long revision,
            long persistedRevision,
            Lifecycle lifecycle,
            boolean loadWritable,
            T data) {

        public Snapshot {
            data = data == null ? null : data.copy();
        }

        @Override
        public T data() {
            return data == null ? null : data.copy();
        }
    }

    private final Map<K, SessionEntry> entries = new ConcurrentHashMap<>();
    private final Map<K, AtomicLong> generations = new ConcurrentHashMap<>();
    private final Map<K, SaveLane> saveLanes = new ConcurrentHashMap<>();
    private final AtomicBoolean sealed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();

    protected void anchor(String fields) {

    }

    protected boolean anchorEnabled() {
        return false;
    }

    private void anchorLateRejection(K key, long expected, long actual, String site) {
        if (!anchorEnabled()) {
            return;
        }
        anchor(Anchors.of()
                .op(key)
                .generation(actual)
                .phase(Anchors.PHASE_REJECTED_LATE)
                .put("expected", expected)
                .put("site", site)
                .render());
    }

    public SessionTicket<K> beginSession(K key, T initialData, boolean loadWritable) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(initialData, "initialData");
        synchronized (lifecycleLock) {
            if (sealed.get()) {
                return null;
            }
            long generation = generations.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
            SessionEntry entry = new SessionEntry(generation, initialData, loadWritable);
            entries.put(key, entry);
            return new SessionTicket<>(key, entry.identity, generation);
        }
    }

    public boolean seal() {
        synchronized (lifecycleLock) {
            return sealed.compareAndSet(false, true);
        }
    }

    public boolean isSealed() {
        return sealed.get();
    }

    public CommitResult installLoaded(SessionTicket<K> ticket, T loaded) {
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

    public CommitResult installLoadFailure(SessionTicket<K> ticket, T fallback) {
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

    public SessionTicket<K> currentTicket(K key) {
        SessionEntry entry = entries.get(key);
        if (sealed.get() || entry == null) {
            return null;
        }
        synchronized (entry) {
            if (!entry.loadWritable || entry.lifecycle != Lifecycle.ACTIVE) {
                return null;
            }
            return new SessionTicket<>(key, entry.identity, entry.generation);
        }
    }

    public long generation(K key) {
        SessionEntry entry = entries.get(key);
        return entry == null ? 0L : entry.generation;
    }

    public boolean isKnownGeneration(K key, long generation) {
        SessionEntry entry = entries.get(key);
        return !sealed.get()
                && entry != null
                && entry.generation == generation
                && entry.lifecycle != Lifecycle.CLOSING;
    }

    public boolean isCurrentGeneration(K key, long generation) {
        SessionEntry entry = entries.get(key);
        return !sealed.get()
                && entry != null
                && entry.generation == generation
                && entry.loadWritable
                && entry.lifecycle == Lifecycle.ACTIVE;
    }

    public Snapshot<T> snapshot(K key) {
        SessionEntry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            return new Snapshot<>(
                    entry.generation,
                    entry.data.revision(),
                    entry.data.persistedRevision(),
                    entry.lifecycle,
                    entry.loadWritable,
                    entry.data
            );
        }
    }

    public T activeData(K key) {
        SessionEntry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            return entry.loadWritable && entry.lifecycle == Lifecycle.ACTIVE ? entry.data.copy() : null;
        }
    }

    public <R> R mutate(K key, long expectedGeneration, Function<T, R> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (sealed.get()) {
            return null;
        }
        SessionEntry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            if (expectedGeneration > 0L && entry.generation != expectedGeneration) {
                anchorLateRejection(key, expectedGeneration, entry.generation, "mutate");
                return null;
            }
            if (!entry.loadWritable || entry.lifecycle != Lifecycle.ACTIVE) {
                return null;
            }
            return mutation.apply(entry.data);
        }
    }

    public SaveTicket<K, T> snapshotForSave(K key, long expectedGeneration, boolean closeAfterSave) {
        SessionEntry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (expectedGeneration > 0L && entry.generation != expectedGeneration) {
            anchorLateRejection(key, expectedGeneration, entry.generation, "snapshotForSave");
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
                    entries.remove(key, entry);
                }
                return null;
            }
            long revision = entry.data.revision();
            if (revision <= entry.data.persistedRevision()) {
                if (closeAfterSave) {
                    entries.remove(key, entry);
                }
                return null;
            }
            SaveTicket<K, T> ticket = new SaveTicket<>(
                    key,
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

    public List<SaveTicket<K, T>> snapshotDirtyEntries() {
        List<SaveTicket<K, T>> tickets = new ArrayList<>();
        for (Map.Entry<K, SessionEntry> mapped : entries.entrySet()) {
            SessionEntry entry = mapped.getValue();
            boolean closeAfterSave;
            long generation;
            synchronized (entry) {
                closeAfterSave = entry.lifecycle == Lifecycle.CLOSING;
                generation = entry.generation;
            }
            SaveTicket<K, T> ticket = snapshotForSave(mapped.getKey(), generation, closeAfterSave);
            if (ticket != null) {
                tickets.add(ticket);
            }
        }
        return List.copyOf(tickets);
    }

    public CompletableFuture<Boolean> enqueueSave(SaveTicket<K, T> ticket,
            Function<SaveTicket<K, T>, CompletableFuture<Boolean>> writer) {
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

            SaveLane lane = saveLanes.computeIfAbsent(ticket.key(), ignored -> new SaveLane());
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

    public CompletableFuture<Void> waitForIdle(K key) {
        SaveLane lane = saveLanes.get(key);
        if (lane == null) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (lane) {
            return lane.tail;
        }
    }

    public CompletableFuture<Void> waitForIdle() {
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

    public Map<K, T> activeDataSnapshot() {
        Map<K, T> snapshot = new LinkedHashMap<>();
        for (Map.Entry<K, SessionEntry> mapped : entries.entrySet()) {
            T data = activeData(mapped.getKey());
            if (data != null) {
                snapshot.put(mapped.getKey(), data);
            }
        }
        return Map.copyOf(snapshot);
    }

    public int dirtyCount() {
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

    public int size() {
        return entries.size();
    }

    protected boolean removeEntry(SessionTicket<K> ticket) {
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return false;
        }
        return entries.remove(ticket.key(), entry);
    }

    private CommitResult commitSaved(SaveTicket<K, T> ticket) {
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
            entries.remove(ticket.key(), entry);
        }
        return CommitResult.COMMITTED;
    }

    private CommitResult commitFailed(SaveTicket<K, T> ticket) {
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

    private SessionEntry currentEntry(SessionTicket<K> ticket) {
        if (ticket == null) {
            return null;
        }
        SessionEntry entry = entries.get(ticket.key());
        return entry != null && matches(entry, ticket) ? entry : null;
    }

    private SessionEntry currentEntry(SaveTicket<K, T> ticket) {
        if (ticket == null) {
            return null;
        }
        SessionEntry entry = entries.get(ticket.key());
        return entry != null && matches(entry, ticket) ? entry : null;
    }

    private boolean matches(SessionEntry entry, SessionTicket<K> ticket) {
        return entry != null
                && ticket != null
                && entry.identity == ticket.entryIdentity()
                && entry.generation == ticket.generation();
    }

    private boolean matches(SessionEntry entry, SaveTicket<K, T> ticket) {
        return entry != null
                && ticket != null
                && entry.identity == ticket.entryIdentity()
                && entry.generation == ticket.generation();
    }

    private static final class SaveLane {

        private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    }

    private final class SessionEntry {

        private final Object identity = new Object();
        private final long generation;
        private T data;
        private boolean loadWritable;
        private Lifecycle lifecycle = Lifecycle.LOADING;
        private SaveTicket<K, T> closeTicket;
        private CompletableFuture<Boolean> closeFuture;

        private SessionEntry(long generation, T data, boolean loadWritable) {
            this.generation = generation;
            this.data = data;
            this.loadWritable = loadWritable;
        }
    }
}
