package emaki.jiuwu.craft.station.queue;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.unlock.UnlockService;
import emaki.jiuwu.craft.station.definition.StationDefinition;

public final class QueueUnlockService {

    private final Map<UUID, QueueUnlocks> loaded = new ConcurrentHashMap<>();
    private final QueueUnlockStore store;
    private final StationQueueUnlockService purchaseService;

    public QueueUnlockService(QueueUnlockStore store, StationQueueUnlockService purchaseService) {
        this.store = store;
        this.purchaseService = purchaseService;
    }

    public CompletableFuture<QueueUnlocks> loadAsync(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(null);
        }
        QueueUnlocks cached = loaded.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return store.loadAsync(playerId).thenApply(unlocks -> {
            QueueUnlocks resolved = unlocks == null ? new QueueUnlocks(playerId) : unlocks;
            QueueUnlocks existing = loaded.putIfAbsent(playerId, resolved);
            return existing == null ? resolved : existing;
        });
    }

    public QueueUnlocks cached(UUID playerId) {
        return playerId == null ? null : loaded.get(playerId);
    }

    public int purchased(UUID playerId, String stationId) {
        QueueUnlocks unlocks = cached(playerId);
        return unlocks == null ? 0 : unlocks.purchased(stationId);
    }

    public CompletableFuture<UnlockService.UnlockResult> purchaseAsync(Player player,
            StationDefinition station,
            int slots) {
        if (player == null || station == null || purchaseService == null) {
            return CompletableFuture.completedFuture(
                    UnlockService.UnlockResult.failed(
                            UnlockService.Quote.rejected(slots, "bad_request"),
                            "bad_request"));
        }
        QueueUnlocks unlocks = cached(player.getUniqueId());
        if (unlocks == null) {
            return CompletableFuture.completedFuture(
                    UnlockService.UnlockResult.failed(
                            UnlockService.Quote.rejected(slots, "not_loaded"),
                            "not_loaded"));
        }
        UnlockService.UnlockResult result =
                purchaseService.purchase(player, station, unlocks, slots);
        if (!result.success()) {
            return CompletableFuture.completedFuture(result);
        }
        return store.saveAsync(unlocks).thenApply(ignored -> result);
    }

    public CompletableFuture<Void> unloadAsync(UUID playerId) {
        QueueUnlocks unlocks = playerId == null ? null : loaded.remove(playerId);
        if (unlocks == null) {
            return CompletableFuture.completedFuture(null);
        }
        return unlocks.dirty() ? store.saveAsync(unlocks) : CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> saveAllAsync() {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (QueueUnlocks unlocks : loaded.values()) {
            if (unlocks.dirty()) {
                chain = chain.thenCompose(ignored -> store.saveAsync(unlocks));
            }
        }
        return chain;
    }
}
