package emaki.jiuwu.craft.station.queue;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.station.definition.StationDefinition;

/**
 * In-memory home for every loaded player's purchased queue slots, plus the purchase lifecycle around them.
 *
 * <p>Mirrors {@link QueueService}: keyed by player, held only while that player is loaded, and mutated only on
 * the owner's owner thread even though the map itself is concurrent.
 *
 * <p>A purchase is flushed to disk immediately rather than waiting for autosave. Queue entries can afford to
 * ride an autosave interval because losing one costs the player materials that a receipt can reconcile; losing
 * a purchase costs them money with nothing to reconcile against.
 */
public final class QueueUnlockService {

    private final Map<UUID, QueueUnlocks> loaded = new ConcurrentHashMap<>();
    private final QueueUnlockStore store;
    private final StationQueueUnlockService purchaseService;

    /**
     * Creates the service.
     *
     * @param store           the persistence layer
     * @param purchaseService the pricing and charging service
     */
    public QueueUnlockService(QueueUnlockStore store, StationQueueUnlockService purchaseService) {
        this.store = store;
        this.purchaseService = purchaseService;
    }

    /**
     * Loads a player's purchased slots into the cache when they are not already there.
     *
     * <p><strong>Thread:</strong> any thread. The future carries no completion-thread guarantee.
     *
     * @param playerId the owner
     * @return a future carrying the cached record
     */
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

    /**
     * Returns an already-cached record without touching disk.
     *
     * @param playerId the owner
     * @return the cached record, or {@code null} when the player is not loaded
     */
    public QueueUnlocks cached(UUID playerId) {
        return playerId == null ? null : loaded.get(playerId);
    }

    /**
     * Reads how many slots a player has bought at one station.
     *
     * @param playerId  the owner
     * @param stationId the station
     * @return the purchased count; zero when the player is not loaded
     */
    public int purchased(UUID playerId, String stationId) {
        QueueUnlocks unlocks = cached(playerId);
        return unlocks == null ? 0 : unlocks.purchased(stationId);
    }

    /**
     * Buys queue slots and flushes the result immediately.
     *
     * <p><strong>Thread:</strong> the buyer's owner thread.
     *
     * @param player  the buyer
     * @param station the station being extended
     * @param slots   how many slots to buy
     * @return a future carrying the outcome
     */
    public CompletableFuture<StationQueueUnlockService.PurchaseResult> purchaseAsync(Player player,
            StationDefinition station,
            int slots) {
        if (player == null || station == null || purchaseService == null) {
            return CompletableFuture.completedFuture(
                    StationQueueUnlockService.PurchaseResult.failed(
                            StationQueueUnlockService.Quote.rejected(slots, "bad_request"),
                            "bad_request"));
        }
        QueueUnlocks unlocks = cached(player.getUniqueId());
        if (unlocks == null) {
            return CompletableFuture.completedFuture(
                    StationQueueUnlockService.PurchaseResult.failed(
                            StationQueueUnlockService.Quote.rejected(slots, "not_loaded"),
                            "not_loaded"));
        }
        StationQueueUnlockService.PurchaseResult result =
                purchaseService.purchase(player, station, unlocks, slots);
        if (!result.success()) {
            return CompletableFuture.completedFuture(result);
        }
        return store.saveAsync(unlocks).thenApply(ignored -> result);
    }

    /**
     * Flushes a player and drops them from the cache.
     *
     * <p><strong>Thread:</strong> any thread. The future carries no completion-thread guarantee.
     *
     * @param playerId the owner
     * @return a future completing once the write finishes
     */
    public CompletableFuture<Void> unloadAsync(UUID playerId) {
        QueueUnlocks unlocks = playerId == null ? null : loaded.remove(playerId);
        if (unlocks == null) {
            return CompletableFuture.completedFuture(null);
        }
        return unlocks.dirty() ? store.saveAsync(unlocks) : CompletableFuture.completedFuture(null);
    }

    /**
     * Flushes every dirty cached record without dropping any of them.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @return a future completing once every write finishes
     */
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
