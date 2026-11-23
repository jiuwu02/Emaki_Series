package emaki.jiuwu.craft.corelib.api.data;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Atomic cross-server shared state: claims, counters, key-value rows and sequences.
 *
 * <p>All operations are single-statement atomic in the shared database, so several
 * sub-servers can claim, count and store against the same keys without a data lease.
 * Namespaces partition key spaces, for example {@code kits.claim} or {@code dungeon.board.<id>}.
 */
public interface SharedStateRepository {

    /**
     * Atomically claims a key once across the network.
     *
     * @param namespace key namespace
     * @param key key to claim
     * @return future completing with {@code true} only for the first caller
     */
    CompletableFuture<Boolean> claimOnce(String namespace, String key);

    /**
     * Atomically adds to a counter, creating it at zero first.
     *
     * @param namespace key namespace
     * @param key counter key
     * @param delta signed amount to add
     * @return future completing with the counter value after the update
     */
    CompletableFuture<Long> incrementCounter(String namespace, String key, long delta);

    /**
     * Reads a counter without modifying it.
     *
     * @param namespace key namespace
     * @param key counter key
     * @return future completing with the current value, {@code 0} when absent
     */
    CompletableFuture<Long> getCounter(String namespace, String key);

    /**
     * Stores a string value, overwriting any previous value.
     *
     * @param namespace key namespace
     * @param key row key
     * @param value text value to store
     * @return future completing when stored
     */
    CompletableFuture<Void> put(String namespace, String key, String value);

    /**
     * Reads one stored value.
     *
     * @param namespace key namespace
     * @param key row key
     * @return future completing with the value, or {@code null} when absent
     */
    CompletableFuture<String> get(String namespace, String key);

    /**
     * Lists every row in one namespace, in insertion order.
     *
     * @param namespace key namespace
     * @return future completing with the rows, possibly empty
     */
    CompletableFuture<List<SharedEntry>> list(String namespace);

    /**
     * Removes one row or counter.
     *
     * @param namespace key namespace
     * @param key row key
     * @return future completing with {@code true} when a row was removed
     */
    CompletableFuture<Boolean> remove(String namespace, String key);

    /**
     * Atomically advances a named sequence.
     *
     * @param name sequence name
     * @return future completing with the new sequence value, starting at 1
     */
    CompletableFuture<Long> nextSequence(String name);
}
