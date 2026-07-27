package emaki.jiuwu.craft.forge.loader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.async.ConcurrentDataStore;
import emaki.jiuwu.craft.forge.model.PlayerData;

final class PlayerDataCache {

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

    record LoadTicket(String uuid, Object entryIdentity, long generation) {
    }

    record SaveTicket(String uuid,
            Object entryIdentity,
            long generation,
            long revision,
            long epoch,
            long persistentRevision,
            PlayerData snapshot,
            boolean closeAfterSave,
            boolean tombstone) {

        SaveTicket {
            snapshot = snapshot == null ? new PlayerData(uuid) : snapshot.copy();
        }

        @Override
        public PlayerData snapshot() {
            return snapshot.copy();
        }
    }

    record VersionedSnapshot(long generation,
            long revision,
            long persistedRevision,
            long epoch,
            long basePersistentRevision,
            Lifecycle lifecycle,
            boolean loadWritable,
            PlayerData snapshot) {

        VersionedSnapshot {
            snapshot = snapshot == null ? null : snapshot.copy();
        }

        @Override
        public PlayerData snapshot() {
            return snapshot == null ? null : snapshot.copy();
        }
    }

    private final Map<String, SessionEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> generations = new ConcurrentHashMap<>();
    private final AtomicBoolean sealed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();

    LoadTicket beginSession(String uuid, boolean existingFile) {
        synchronized (lifecycleLock) {
            if (sealed.get()) {
                return null;
            }
            String key = normalize(uuid);
            long generation = generations.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
            SessionEntry entry = new SessionEntry(key, generation, !existingFile);
            entries.put(key, entry);
            return new LoadTicket(key, entry.identity, generation);
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

    CommitResult installLoaded(LoadTicket ticket, PlayerData loaded) {
        return installLoaded(ticket, loaded, 1L, 0L);
    }

    CommitResult installLoaded(LoadTicket ticket,
            PlayerData loaded,
            long epoch,
            long basePersistentRevision) {
        if (sealed.get()) {
            return CommitResult.REJECTED;
        }
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return CommitResult.STALE;
        }
        synchronized (entry) {
            if (!matches(entry, ticket) || entry.lifecycle != Lifecycle.LOADING || entry.store.version() != 0L) {
                return CommitResult.STALE;
            }
            entry.store = new ConcurrentDataStore<>(loaded == null ? new PlayerData(ticket.uuid()) : loaded);
            entry.persistedRevision = 0L;
            entry.epoch = Math.max(1L, epoch);
            entry.basePersistentRevision = Math.max(0L, basePersistentRevision);
            entry.loadWritable = true;
            entry.lifecycle = Lifecycle.ACTIVE;
            return CommitResult.COMMITTED;
        }
    }

    CommitResult installLoadFailure(LoadTicket ticket, PlayerData fallback) {
        if (sealed.get()) {
            return CommitResult.REJECTED;
        }
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return CommitResult.STALE;
        }
        synchronized (entry) {
            if (!matches(entry, ticket) || entry.lifecycle != Lifecycle.LOADING || entry.store.version() != 0L) {
                return CommitResult.STALE;
            }
            entry.store = new ConcurrentDataStore<>(fallback == null ? new PlayerData(ticket.uuid()) : fallback);
            entry.persistedRevision = 0L;
            entry.loadWritable = false;
            entry.lifecycle = Lifecycle.LOAD_FAILED;
            return CommitResult.COMMITTED;
        }
    }

    boolean contains(String uuid) {
        return entries.containsKey(normalize(uuid));
    }

    long generation(String uuid) {
        SessionEntry entry = entries.get(normalize(uuid));
        return entry == null ? 0L : entry.generation;
    }

    boolean isCurrentGeneration(String uuid, long generation) {
        SessionEntry entry = entries.get(normalize(uuid));
        return !sealed.get()
                && entry != null
                && entry.generation == generation
                && entry.lifecycle != Lifecycle.CLOSING;
    }

    boolean isWritable(String uuid) {
        SessionEntry entry = entries.get(normalize(uuid));
        return !sealed.get()
                && entry != null
                && entry.loadWritable
                && entry.lifecycle == Lifecycle.ACTIVE;
    }

    <R> R read(String uuid, Function<PlayerData, R> reader) {
        Objects.requireNonNull(reader, "reader");
        SessionEntry entry = entries.get(normalize(uuid));
        if (entry == null) {
            return null;
        }
        return entry.store.read(reader);
    }

    boolean update(String uuid, long expectedGeneration, Consumer<PlayerData> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (sealed.get()) {
            return false;
        }
        SessionEntry entry = entries.get(normalize(uuid));
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            if (expectedGeneration > 0L && entry.generation != expectedGeneration) {
                return false;
            }
            if (!entry.loadWritable
                    || entry.lifecycle == Lifecycle.CLOSING
                    || entry.lifecycle == Lifecycle.LOAD_FAILED) {
                return false;
            }
            entry.store.write(data -> {
                consumer.accept(data);
                return data;
            });
            if (entry.lifecycle == Lifecycle.LOADING) {
                entry.lifecycle = entry.loadWritable ? Lifecycle.ACTIVE : Lifecycle.LOAD_FAILED;
            }
            return true;
        }
    }

    VersionedSnapshot snapshot(String uuid) {
        SessionEntry entry = entries.get(normalize(uuid));
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            ConcurrentDataStore.VersionedValue<PlayerData> versioned = entry.store.readVersioned(PlayerData::copy);
            return new VersionedSnapshot(
                    entry.generation,
                    versioned.version(),
                    entry.persistedRevision,
                    entry.epoch,
                    entry.basePersistentRevision,
                    entry.lifecycle,
                    entry.loadWritable,
                    versioned.value()
            );
        }
    }

    SaveTicket snapshotForSave(String uuid, boolean closeAfterSave) {
        return snapshotForSave(uuid, 0L, closeAfterSave);
    }

    SaveTicket snapshotForSave(String uuid, long expectedGeneration, boolean closeAfterSave) {
        SessionEntry entry = entries.get(normalize(uuid));
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
                return null;
            }
            ConcurrentDataStore.VersionedValue<PlayerData> versioned = entry.store.readVersioned(PlayerData::copy);
            if (!closeAfterSave && versioned.version() <= entry.persistedRevision) {
                return null;
            }
            SaveTicket ticket = new SaveTicket(
                    entry.uuid,
                    entry.identity,
                    entry.generation,
                    versioned.version(),
                    entry.epoch,
                    entry.basePersistentRevision + versioned.version(),
                    versioned.value(),
                    closeAfterSave,
                    false
            );
            if (closeAfterSave) {
                entry.closeTicket = ticket;
            }
            return ticket;
        }
    }

    List<SaveTicket> snapshotDirtyEntries() {
        List<SaveTicket> tickets = new ArrayList<>();
        for (Map.Entry<String, SessionEntry> mapped : entries.entrySet()) {
            SessionEntry entry = mapped.getValue();
            boolean closeAfterSave;
            synchronized (entry) {
                closeAfterSave = entry.lifecycle == Lifecycle.CLOSING;
            }
            SaveTicket ticket = snapshotForSave(mapped.getKey(), closeAfterSave);
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

            CompletableFuture<Void> previous = entry.saveTail;
            CompletableFuture<Boolean> save = previous.handle((ignored, throwable) -> null)
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
            entry.saveTail = save.handle((ignored, throwable) -> null);
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

    CompletableFuture<Void> waitForIdle() {
        List<CompletableFuture<Void>> tails = new ArrayList<>();
        for (SessionEntry entry : entries.values()) {
            synchronized (entry) {
                tails.add(entry.saveTail);
            }
        }
        return tails.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(tails.toArray(CompletableFuture[]::new));
    }

    CommitResult commitSaved(SaveTicket ticket) {
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return CommitResult.STALE;
        }
        boolean remove = false;
        synchronized (entry) {
            if (!matches(entry, ticket)) {
                return CommitResult.STALE;
            }
            entry.persistedRevision = Math.max(entry.persistedRevision, ticket.revision());
            if (ticket.closeAfterSave() && entry.lifecycle == Lifecycle.CLOSING) {
                long currentRevision = entry.store.version();
                if (currentRevision == ticket.revision() && Objects.equals(entry.closeTicket, ticket)) {
                    remove = true;
                } else if (Objects.equals(entry.closeTicket, ticket)) {
                    entry.closeTicket = null;
                    entry.closeFuture = null;
                }
            }
        }
        if (remove) {
            entries.remove(ticket.uuid(), entry);
        }
        return CommitResult.COMMITTED;
    }

    CommitResult commitFailed(SaveTicket ticket) {
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
                if (entry.lifecycle == Lifecycle.CLOSING) {
                    entry.lifecycle = entry.loadWritable ? Lifecycle.ACTIVE : Lifecycle.LOAD_FAILED;
                }
            }
            return CommitResult.COMMITTED;
        }
    }

    boolean removeIfCurrent(String uuid, Object entryIdentity, long generation) {
        SessionEntry entry = entries.get(normalize(uuid));
        return entry != null
                && entry.identity == entryIdentity
                && entry.generation == generation
                && entries.remove(entry.uuid, entry);
    }

    boolean removeCurrent(String uuid) {
        SessionEntry entry = entries.get(normalize(uuid));
        return entry != null && entries.remove(entry.uuid, entry);
    }

    int size() {
        return entries.size();
    }

    int dirtyCount() {
        int count = 0;
        for (SessionEntry entry : entries.values()) {
            synchronized (entry) {
                if (entry.loadWritable && entry.store.version() > entry.persistedRevision) {
                    count++;
                }
            }
        }
        return count;
    }

    private SessionEntry currentEntry(LoadTicket ticket) {
        if (ticket == null) {
            return null;
        }
        SessionEntry entry = entries.get(normalize(ticket.uuid()));
        return entry != null && matches(entry, ticket) ? entry : null;
    }

    private SessionEntry currentEntry(SaveTicket ticket) {
        if (ticket == null) {
            return null;
        }
        SessionEntry entry = entries.get(normalize(ticket.uuid()));
        return entry != null && matches(entry, ticket) ? entry : null;
    }

    private boolean matches(SessionEntry entry, LoadTicket ticket) {
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

    private String normalize(String uuid) {
        return uuid == null ? "" : uuid;
    }

    private static final class SessionEntry {

        private final String uuid;
        private final Object identity = new Object();
        private final long generation;
        private ConcurrentDataStore<PlayerData> store;
        private long persistedRevision;
        private long epoch = 1L;
        private long basePersistentRevision;
        private Lifecycle lifecycle = Lifecycle.LOADING;
        private boolean loadWritable;
        private CompletableFuture<Void> saveTail = CompletableFuture.completedFuture(null);
        private SaveTicket closeTicket;
        private CompletableFuture<Boolean> closeFuture;

        private SessionEntry(String uuid, long generation, boolean loadWritable) {
            this.uuid = uuid;
            this.generation = generation;
            this.loadWritable = loadWritable;
            this.store = new ConcurrentDataStore<>(new PlayerData(uuid));
        }
    }
}
