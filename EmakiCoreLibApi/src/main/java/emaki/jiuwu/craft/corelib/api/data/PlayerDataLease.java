package emaki.jiuwu.craft.corelib.api.data;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Cross-server single-writer lease on player data.
 *
 * <p>While a lease is held by this server it is renewed automatically in the background;
 * {@link #release} stops renewal and frees the lease for other servers.
 */
public interface PlayerDataLease {

    /**
     * Acquires or renews the single-writer lease for one player.
     *
     * @param player player UUID; must not be {@code null}
     * @return future completing with {@code true} when this server holds the lease
     */
    CompletableFuture<Boolean> acquire(UUID player);

    /**
     * Releases the lease held by this server for one player.
     *
     * @param player player UUID; must not be {@code null}
     * @return future completing with {@code true} when a lease row was removed
     */
    CompletableFuture<Boolean> release(UUID player);
}
