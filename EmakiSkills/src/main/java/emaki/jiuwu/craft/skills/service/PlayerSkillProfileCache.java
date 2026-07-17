package emaki.jiuwu.craft.skills.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Function;

import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;

final class PlayerSkillProfileCache {

    enum Lifecycle {
        LOADING,
        ACTIVE,
        CLOSING,
        LOAD_FAILED
    }

    enum CommitResult {
        COMMITTED,
        STALE,
        RETRY,
        REJECTED
    }

    record SessionTicket(UUID playerId, Object entryIdentity, long generation) {
    }

    record SaveTicket(UUID playerId,
            Object entryIdentity,
            long generation,
            long revision,
            PlayerSkillProfile snapshot,
            boolean closeAfterSave) {

        SaveTicket {
            snapshot = snapshot == null ? null : snapshot.copy();
        }

        @Override
        public PlayerSkillProfile snapshot() {
            return snapshot == null ? null : snapshot.copy();
        }
    }

    record Snapshot(long generation,
            long revision,
            long persistedRevision,
            Lifecycle lifecycle,
            boolean loadWritable,
            PlayerSkillProfile profile) {

        Snapshot {
            profile = profile == null ? null : profile.copy();
        }

        @Override
        public PlayerSkillProfile profile() {
            return profile == null ? null : profile.copy();
        }
    }

    private final Map<UUID, SessionEntry> entries = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> generations = new ConcurrentHashMap<>();
    private final Map<UUID, SaveLane> saveLanes = new ConcurrentHashMap<>();
    private final AtomicBoolean sealed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();

    SessionTicket beginSession(UUID playerId, PlayerSkillProfile initialProfile, boolean loadWritable) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(initialProfile, "initialProfile");
        synchronized (lifecycleLock) {
            if (sealed.get()) {
                return null;
            }
            SessionEntry existing = entries.get(playerId);
            if (existing != null) {
                synchronized (existing) {
                    if (existing.lifecycle == Lifecycle.CLOSING && existing.profile.isDirty()) {
                        existing.lifecycle = Lifecycle.ACTIVE;
                        existing.closeTicket = null;
                        existing.closeFuture = null;
                        return new SessionTicket(playerId, existing.identity, existing.generation);
                    }
                }
            }
            long generation = generations.computeIfAbsent(playerId, ignored -> new AtomicLong()).incrementAndGet();
            SessionEntry entry = new SessionEntry(playerId, generation, initialProfile, loadWritable);
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

    CommitResult installLoaded(SessionTicket ticket, PlayerSkillProfile loaded) {
        if (sealed.get()) {
            return CommitResult.REJECTED;
        }
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return CommitResult.STALE;
        }
        synchronized (entry) {
            if (!matches(entry, ticket)
                    || entry.lifecycle != Lifecycle.LOADING
                    || entry.profile.revision() != 0L) {
                return CommitResult.STALE;
            }
            entry.profile = loaded == null ? entry.profile : loaded;
            entry.profile.clearDirty();
            entry.loadWritable = true;
            entry.lifecycle = Lifecycle.ACTIVE;
            return CommitResult.COMMITTED;
        }
    }

    CommitResult installLoadFailure(SessionTicket ticket, PlayerSkillProfile fallback) {
        if (sealed.get()) {
            return CommitResult.REJECTED;
        }
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return CommitResult.STALE;
        }
        synchronized (entry) {
            if (!matches(entry, ticket)
                    || entry.lifecycle != Lifecycle.LOADING
                    || entry.profile.revision() != 0L) {
                return CommitResult.STALE;
            }
            entry.profile = fallback == null ? entry.profile : fallback;
            entry.profile.clearDirty();
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

    boolean isCurrent(SessionTicket ticket) {
        if (sealed.get()) {
            return false;
        }
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            return entry.loadWritable && entry.lifecycle == Lifecycle.ACTIVE;
        }
    }

    PlayerSkillProfile profile(UUID playerId) {
        SessionTicket ticket = currentTicket(playerId);
        return profile(ticket);
    }

    PlayerSkillProfile profile(SessionTicket ticket) {
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            return entry.loadWritable && entry.lifecycle == Lifecycle.ACTIVE ? entry.profile.copy() : null;
        }
    }

    <R> R readIfCurrent(SessionTicket ticket, Function<PlayerSkillProfile, R> reader) {
        Objects.requireNonNull(reader, "reader");
        SessionEntry entry = currentEntry(ticket);
        if (sealed.get() || entry == null) {
            return null;
        }
        synchronized (entry) {
            if (!entry.loadWritable || entry.lifecycle != Lifecycle.ACTIVE) {
                return null;
            }
            return reader.apply(entry.profile.copy());
        }
    }

    boolean mutateIfCurrent(SessionTicket ticket, Consumer<PlayerSkillProfile> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        SessionEntry entry = currentEntry(ticket);
        if (sealed.get() || entry == null) {
            return false;
        }
        synchronized (entry) {
            if (!entry.loadWritable || entry.lifecycle != Lifecycle.ACTIVE) {
                return false;
            }
            mutation.accept(entry.profile);
            return true;
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
            long revision = entry.profile.revision();
            if (revision <= entry.profile.persistedRevision()) {
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
                    entry.profile.copy(),
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
            synchronized (entry) {
                closeAfterSave = entry.lifecycle == Lifecycle.CLOSING;
            }
            SaveTicket ticket = snapshotForSave(mapped.getKey(), entry.generation, closeAfterSave);
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
                entry.saveTail = lane.tail;
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

    int dirtyCount() {
        int count = 0;
        for (SessionEntry entry : entries.values()) {
            synchronized (entry) {
                if (entry.loadWritable && entry.profile.isDirty()) {
                    count++;
                }
            }
        }
        return count;
    }

    int size() {
        return entries.size();
    }

    Snapshot snapshot(UUID playerId) {
        SessionEntry entry = entries.get(playerId);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            return new Snapshot(
                    entry.generation,
                    entry.profile.revision(),
                    entry.profile.persistedRevision(),
                    entry.lifecycle,
                    entry.loadWritable,
                    entry.profile.copy()
            );
        }
    }

    private CommitResult commitSaved(SaveTicket ticket) {
        SessionEntry entry = currentEntry(ticket);
        if (entry == null) {
            return CommitResult.STALE;
        }
        boolean remove = false;
        CommitResult result = CommitResult.COMMITTED;
        synchronized (entry) {
            if (!matches(entry, ticket)) {
                return CommitResult.STALE;
            }
            entry.profile.markPersisted(ticket.revision());
            if (ticket.closeAfterSave() && entry.lifecycle == Lifecycle.CLOSING) {
                if (entry.profile.revision() == ticket.revision()
                        && Objects.equals(entry.closeTicket, ticket)) {
                    remove = true;
                } else if (Objects.equals(entry.closeTicket, ticket)) {
                    entry.closeTicket = null;
                    entry.closeFuture = null;
                    result = CommitResult.RETRY;
                }
            }
        }
        if (remove) {
            entries.remove(ticket.playerId(), entry);
        }
        return result;
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
        private PlayerSkillProfile profile;
        private boolean loadWritable;
        private Lifecycle lifecycle = Lifecycle.LOADING;
        private CompletableFuture<Void> saveTail = CompletableFuture.completedFuture(null);
        private SaveTicket closeTicket;
        private CompletableFuture<Boolean> closeFuture;

        private SessionEntry(UUID playerId,
                long generation,
                PlayerSkillProfile profile,
                boolean loadWritable) {
            this.playerId = playerId;
            this.generation = generation;
            this.profile = profile;
            this.loadWritable = loadWritable;
        }
    }
}
