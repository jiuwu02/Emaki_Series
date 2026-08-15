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

public final class QueueService {

    private final Map<UUID, PlayerQueues> loaded = new ConcurrentHashMap<>();
    private final QueueStore store;

    public QueueService(QueueStore store) {
        this.store = store;
    }

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

    public PlayerQueues cached(UUID playerId) {
        return playerId == null ? null : loaded.get(playerId);
    }

    public List<PlayerQueues> allCached() {
        return List.copyOf(loaded.values());
    }

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

    public CompletableFuture<Void> saveAsync(UUID playerId) {
        PlayerQueues queues = cached(playerId);
        if (queues == null) {
            return CompletableFuture.completedFuture(null);
        }
        return store.saveAsync(queues);
    }

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

    public QueueSnapshot snapshot(Player player, StationDefinition station, PlayerQueues queues) {
        ProgressMode mode = station.progressMode();
        int maxLength = QueueCapacity.effectiveLength(player, station);
        CraftQueue queue = queues.existingQueue(station.id());
        if (queue == null) {
            return new QueueSnapshot(queues.playerId(), station.id(), List.of(), maxLength, mode);
        }
        return queue.toSnapshot(mode, maxLength, System.currentTimeMillis());
    }

    public QueueSnapshot snapshot(UUID playerId, StationDefinition station, PlayerQueues queues) {
        ProgressMode mode = station.progressMode();
        int maxLength = station.queueSettings().baseLength();
        CraftQueue queue = queues.existingQueue(station.id());
        if (queue == null) {
            return new QueueSnapshot(playerId, station.id(), List.of(), maxLength, mode);
        }
        return queue.toSnapshot(mode, maxLength, System.currentTimeMillis());
    }

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

    public record ClaimableEntry(StationDefinition station,
            CraftQueue queue,
            QueueEntry entry,
            List<PendingOutput> outputs) {
    }
}
