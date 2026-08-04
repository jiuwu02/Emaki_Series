package emaki.jiuwu.craft.station.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.station.api.model.PendingOutput;
import emaki.jiuwu.craft.station.api.model.ProgressMode;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;
import emaki.jiuwu.craft.station.api.model.QueueSnapshot;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.definition.StationRegistry;

/**
 * In-memory home for every loaded player's queues, plus the load/save lifecycle around them.
 *
 * <p>Queues are keyed by player and held only while that player is loaded. The cache is a
 * {@link ConcurrentHashMap} for safe cross-thread lookup, but each {@link PlayerQueues} instance is only
 * ever mutated on its owner's owner thread, which is where the callers in this module operate.
 */
public final class QueueService {

    private final Map<UUID, PlayerQueues> loaded = new ConcurrentHashMap<>();
    private final QueueStore store;

    /**
     * Creates the service.
     *
     * @param store the persistence layer
     */
    public QueueService(QueueStore store) {
        this.store = store;
    }

    /**
     * Loads a player's queues into the cache when they are not already there.
     *
     * <p><strong>Thread:</strong> any thread. The future carries no completion-thread guarantee.
     *
     * @param playerId the owner
     * @return a future carrying the cached queues
     */
    public CompletableFuture<PlayerQueues> loadAsync(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerQueues cached = loaded.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return store.loadAsync(playerId).thenApply(queues -> {
            PlayerQueues resolved = queues == null ? new PlayerQueues(playerId) : queues;
            PlayerQueues existing = loaded.putIfAbsent(playerId, resolved);
            return existing == null ? resolved : existing;
        });
    }

    /**
     * Returns already-cached queues without touching disk.
     *
     * @param playerId the owner
     * @return the cached queues, or {@code null} when the player is not loaded
     */
    public PlayerQueues cached(UUID playerId) {
        return playerId == null ? null : loaded.get(playerId);
    }

    /** {@return every currently cached player} */
    public List<PlayerQueues> allCached() {
        return List.copyOf(loaded.values());
    }

    /**
     * Flushes a player and drops them from the cache.
     *
     * <p>Online progress is folded in before the write so a saved entry never carries a live tick timestamp
     * that a restart would misread as elapsed time.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param playerId the owner
     * @return a future completing once the write finishes
     */
    public CompletableFuture<Void> unloadAsync(UUID playerId) {
        PlayerQueues queues = playerId == null ? null : loaded.remove(playerId);
        if (queues == null) {
            return CompletableFuture.completedFuture(null);
        }
        long now = System.currentTimeMillis();
        for (CraftQueue queue : queues.all()) {
            queue.freezeAll(now);
        }
        return store.saveAsync(queues);
    }

    /**
     * Saves one player without dropping them from the cache.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param playerId the owner
     * @return a future completing once the write finishes
     */
    public CompletableFuture<Void> saveAsync(UUID playerId) {
        PlayerQueues queues = cached(playerId);
        if (queues == null) {
            return CompletableFuture.completedFuture(null);
        }
        return store.saveAsync(queues);
    }

    /**
     * Saves every cached player whose data changed.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @return a future completing once every write finishes
     */
    public CompletableFuture<Void> saveDirtyAsync() {
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (PlayerQueues queues : loaded.values()) {
            if (queues.dirty()) {
                writes.add(store.saveAsync(queues));
            }
        }
        if (writes.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    /**
     * Flushes every cached player and clears the cache.
     *
     * <p>Used by the shutdown path, which is why it saves unconditionally rather than only when dirty: a
     * missed write at shutdown is not recoverable later.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @return a future completing once every write finishes
     */
    public CompletableFuture<Void> flushAllAsync() {
        List<PlayerQueues> snapshot = List.copyOf(loaded.values());
        loaded.clear();
        if (snapshot.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        long now = System.currentTimeMillis();
        List<CompletableFuture<Void>> writes = new ArrayList<>(snapshot.size());
        for (PlayerQueues queues : snapshot) {
            for (CraftQueue queue : queues.all()) {
                queue.freezeAll(now);
            }
            writes.add(store.saveAsync(queues));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    /**
     * Folds elapsed online progress into a player's queues and freezes their clocks.
     *
     * @param playerId the owner
     */
    public void freezeOnlineProgress(UUID playerId) {
        PlayerQueues queues = cached(playerId);
        if (queues == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (CraftQueue queue : queues.all()) {
            queue.freezeAll(now);
        }
        queues.markDirty();
    }

    /**
     * Resumes online progress for a player's queues.
     *
     * @param playerId the owner
     */
    public void resumeOnlineProgress(UUID playerId) {
        PlayerQueues queues = cached(playerId);
        if (queues == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (CraftQueue queue : queues.all()) {
            queue.resumeAll(now);
        }
    }

    /**
     * Builds a snapshot of one queue.
     *
     * @param player    the viewing player, used to resolve their permission tier
     * @param station   the station definition
     * @param queues    the owner's queues
     * @return the snapshot
     */
    public QueueSnapshot snapshot(Player player, StationDefinition station, PlayerQueues queues) {
        ProgressMode mode = station.progressMode();
        int maxLength = QueueCapacity.effectiveLength(player, station);
        CraftQueue queue = queues.existingQueue(station.id());
        if (queue == null) {
            return new QueueSnapshot(queues.playerId(), station.id(), List.of(), maxLength, mode);
        }
        return queue.toSnapshot(mode, maxLength, System.currentTimeMillis());
    }

    /**
     * Builds a snapshot for an arbitrary, possibly offline owner.
     *
     * @param playerId the owner
     * @param station  the station definition
     * @param queues   the owner's queues
     * @return the snapshot
     */
    public QueueSnapshot snapshot(UUID playerId, StationDefinition station, PlayerQueues queues) {
        ProgressMode mode = station.progressMode();
        int maxLength = station.queueSettings().baseLength();
        CraftQueue queue = queues.existingQueue(station.id());
        if (queue == null) {
            return new QueueSnapshot(playerId, station.id(), List.of(), maxLength, mode);
        }
        return queue.toSnapshot(mode, maxLength, System.currentTimeMillis());
    }

    /**
     * Collects every pending-claim entry a player owns, paired with the station it belongs to.
     *
     * @param registry the resolved registry, used to skip entries whose station no longer exists
     * @param queues   the owner's queues
     * @return the claimable entries
     */
    public List<ClaimableEntry> claimable(StationRegistry registry, PlayerQueues queues) {
        if (queues == null) {
            return List.of();
        }
        List<ClaimableEntry> claimable = new ArrayList<>();
        for (CraftQueue queue : queues.all()) {
            StationDefinition station = registry.station(queue.stationId());
            if (station == null) {
                continue;
            }
            for (QueueEntry entry : List.copyOf(queue.entries())) {
                if (entry.state() == QueueEntryState.PENDING_CLAIM && !entry.pendingOutputs().isEmpty()) {
                    claimable.add(new ClaimableEntry(station, queue, entry,
                            List.copyOf(entry.pendingOutputs())));
                }
            }
        }
        return claimable;
    }

    /**
     * One pending-claim entry together with the context needed to deliver it.
     *
     * @param station the station the entry belongs to
     * @param queue   the queue holding the entry
     * @param entry   the entry itself
     * @param outputs the outputs it still owes
     */
    public record ClaimableEntry(StationDefinition station,
            CraftQueue queue,
            QueueEntry entry,
            List<PendingOutput> outputs) {
    }
}
