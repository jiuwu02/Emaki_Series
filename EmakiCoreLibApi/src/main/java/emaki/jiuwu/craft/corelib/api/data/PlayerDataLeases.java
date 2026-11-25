package emaki.jiuwu.craft.corelib.api.data;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Shared retry loop for acquiring a {@link PlayerDataLease}.
 *
 * <p>The loop retries every two seconds and fails with {@link IllegalStateException} once the
 * attempt budget is exhausted, so that a server which cannot win the single-writer lease reports
 * a load failure instead of blocking a player session forever.
 */
public final class PlayerDataLeases {

    private PlayerDataLeases() {
    }

    /**
     * Acquires the lease, retrying on refusal until the attempt budget is spent.
     *
     * @param lease lease implementation to drive; must not be {@code null}
     * @param playerId player UUID used for the lease row and the failure message
     * @param attemptsLeft remaining attempts, including the current one
     * @return future completing with {@code null} when the lease is held, or failing with
     *         {@link IllegalStateException} when the budget is exhausted
     */
    public static CompletableFuture<Void> acquireWithRetry(PlayerDataLease lease, UUID playerId, int attemptsLeft) {
        return lease.acquire(playerId).thenCompose(acquired -> {
            if (Boolean.TRUE.equals(acquired)) {
                return CompletableFuture.completedFuture(null);
            }
            if (attemptsLeft <= 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("数据租约获取失败: " + playerId));
            }
            return CompletableFuture.runAsync(() -> { },
                            CompletableFuture.delayedExecutor(2L, TimeUnit.SECONDS))
                    .thenCompose(ignored -> acquireWithRetry(lease, playerId, attemptsLeft - 1));
        });
    }
}
