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

/**
 * Shared generation / seal / save-lane mechanics for per-player session caches.
 *
 * <p>Five business modules grew their own near-identical copy of this machinery, each redeclaring
 * {@code Lifecycle}, {@code CommitResult}, {@code SessionTicket}, {@code SaveTicket} and
 * {@code SaveLane}. That duplication was the missing abstraction layer in CoreLib rather than modules
 * ignoring existing infrastructure, so the mechanics live here and modules keep only their data codec
 * and business hooks.
 *
 * <h2>Why tickets exist</h2>
 *
 * <p>A player can quit and rejoin while an asynchronous save from the previous session is still in
 * flight. Every session gets a monotonically increasing {@code generation} plus an identity token;
 * a late callback carrying an old ticket is recognised as {@link CommitResult#STALE} and is refused
 * instead of overwriting the new session's state. This is the region-migration guarantee that F-16
 * exercises.
 *
 * <h2>Threading</h2>
 *
 * <p>Entry state is guarded by the entry monitor; session creation and sealing are guarded by a
 * separate lifecycle lock. The lock order is always {@code lifecycleLock → entry}, and
 * {@code lane → entry} for save submission. No Bukkit API is touched, so any thread may call these
 * methods; the writer callback decides its own execution context.
 *
 * @param <K> cache key type. Most modules key by {@code UUID}; Forge keys by {@code String}
 * @param <T> cached payload type
 */
public abstract class AbstractPlayerSessionCache<K, T extends SessionData<T>> {

    /** Lifecycle states a session entry moves through. */
    public enum Lifecycle {
        /** Created, payload not yet loaded from storage. */
        LOADING,
        /** Loaded and writable. */
        ACTIVE,
        /** Closing; a final save may still be in flight. */
        CLOSING,
        /** Load failed; the entry is read-only so an empty payload never overwrites a real file. */
        LOAD_FAILED
    }

    /** Outcome of a commit attempt against a ticket. */
    public enum CommitResult {
        /** The ticket matched the current entry and the commit was applied. */
        COMMITTED,
        /** The ticket belongs to a superseded session; the commit was refused. */
        STALE,
        /** The cache is sealed; no further commits are accepted. */
        REJECTED
    }

    /** Handle identifying one session of one key. */
    public record SessionTicket<K>(K key, Object entryIdentity, long generation) {
    }

    /**
     * Handle identifying one save attempt, carrying an isolated snapshot.
     *
     * <p>The snapshot is copied both on construction and on read, so neither the submitter nor the
     * writer can observe or cause a concurrent modification of the live payload.
     */
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

    /** Read-only view of an entry's state, for diagnostics and callers that must not mutate. */
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

    /**
     * 记录一条持久化锚点（W6-2a）。**默认为 no-op**，子类按需覆写以接到自己的
     * {@code DebugLogger}，因此锚点默认关闭、无输出、无额外开销。
     *
     * <p>调用方传入的是 {@link Anchors} 渲染好的字段串（如
     * {@code op=... gen=... phase=rejected_late}），本类不做拼装也不判断开关 ——
     * 保持基类对日志设施零依赖。
     *
     * <p>覆写实现**不得抛异常**：锚点是诊断附属品，不应影响会话本身的正确性。
     *
     * @param fields 已渲染的锚点字段串，非空
     */
    protected void anchor(String fields) {
        // no-op by default: subclasses opt in
    }

    /**
     * {@return whether anchor recording is worth building}
     *
     * 默认 {@code false}，使 {@link Anchors} 的构造在未开启时被完全跳过。
     * 子类应在此返回自身 debug 开关的状态。
     */
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

    /**
     * Opens a new session, superseding any existing one for the same key.
     *
     * @param key          cache key
     * @param initialData  placeholder payload used until the load completes
     * @param loadWritable whether the placeholder may already be written back
     * @return the new session ticket, or {@code null} when the cache is sealed
     */
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

    /** Seals the cache so no new sessions or commits are accepted. @return {@code true} on the first call */
    public boolean seal() {
        synchronized (lifecycleLock) {
            return sealed.compareAndSet(false, true);
        }
    }

    /** @return whether the cache has been sealed */
    public boolean isSealed() {
        return sealed.get();
    }

    /**
     * Installs a successfully loaded payload, making the session writable.
     *
     * @param ticket session ticket returned by {@link #beginSession}
     * @param loaded loaded payload; {@code null} keeps the placeholder
     * @return the commit outcome
     */
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

    /**
     * Installs a load failure, leaving the session read-only.
     *
     * <p>Read-only is the point: an empty placeholder must never be written over a file that failed
     * to load, or a transient IO error would silently erase player data.
     *
     * @param ticket   session ticket
     * @param fallback payload to expose; {@code null} keeps the placeholder
     * @return the commit outcome
     */
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

    /** @return a ticket for the current writable session, or {@code null} when absent/not writable */
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

    /** @return the current generation for the key, or {@code 0} when no session exists */
    public long generation(K key) {
        SessionEntry entry = entries.get(key);
        return entry == null ? 0L : entry.generation;
    }

    /** @return whether the generation is the live one and not closing */
    public boolean isKnownGeneration(K key, long generation) {
        SessionEntry entry = entries.get(key);
        return !sealed.get()
                && entry != null
                && entry.generation == generation
                && entry.lifecycle != Lifecycle.CLOSING;
    }

    /** @return whether the generation is the live one and currently writable */
    public boolean isCurrentGeneration(K key, long generation) {
        SessionEntry entry = entries.get(key);
        return !sealed.get()
                && entry != null
                && entry.generation == generation
                && entry.loadWritable
                && entry.lifecycle == Lifecycle.ACTIVE;
    }

    /** @return an isolated view of the entry state, or {@code null} when no session exists */
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

    /** @return a copy of the active payload, or {@code null} when not active/writable */
    public T activeData(K key) {
        SessionEntry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            return entry.loadWritable && entry.lifecycle == Lifecycle.ACTIVE ? entry.data.copy() : null;
        }
    }

    /**
     * Applies a mutation under the entry lock.
     *
     * @param expectedGeneration generation the caller believes is current; {@code 0} skips the check
     * @return the mutation result, or {@code null} when the session is gone, stale or not writable
     */
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

    /**
     * Captures an isolated save ticket, or {@code null} when there is nothing to persist.
     *
     * @param closeAfterSave whether the entry should be removed once the save commits
     */
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

    /** @return save tickets for every dirty entry, for shutdown drain */
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

    /**
     * Submits a save through the per-key lane, keeping writes for one key strictly ordered.
     *
     * @param writer performs the actual write; may return {@code null} for "nothing written"
     * @return {@code true} when the write succeeded and the commit was applied
     */
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

    /** @return a future completing when the key's save lane is idle */
    public CompletableFuture<Void> waitForIdle(K key) {
        SaveLane lane = saveLanes.get(key);
        if (lane == null) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (lane) {
            return lane.tail;
        }
    }

    /** @return a future completing when every save lane is idle */
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

    /** @return copies of every active payload, keyed by cache key */
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

    /** @return how many writable entries have unpersisted mutations */
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

    /** @return how many sessions are currently held */
    public int size() {
        return entries.size();
    }

    /** Removes the entry for a key when the ticket still matches. @return whether it was removed */
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

