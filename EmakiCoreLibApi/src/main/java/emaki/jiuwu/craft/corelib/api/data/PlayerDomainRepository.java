package emaki.jiuwu.craft.corelib.api.data;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Revision-guarded storage for per-player domain payloads in the shared database.
 *
 * <p>Every save is an optimistic-lock compare-and-set against {@code expectedRevision}:
 * pass {@code 0} to create a row, or the revision returned by the last load or save to
 * update it. A save that loses the lock answers {@link DomainSaveResult.Outcome#STALE}
 * instead of overwriting newer data.
 */
public interface PlayerDomainRepository {

    /**
     * Loads the stored payload for one player and domain.
     *
     * @param player player UUID; must not be {@code null}
     * @param domain domain identifier, for example {@code level}
     * @return future completing with the stored snapshot, or {@code null} when no row exists
     */
    CompletableFuture<DomainSnapshot> load(UUID player, String domain);

    /**
     * Stores a payload with an optimistic-lock guard.
     *
     * @param player player UUID; must not be {@code null}
     * @param domain domain identifier
     * @param playerName current player name recorded in the shared identity domain
     * @param payload serialized domain payload
     * @param schemaVersion payload format version to record
     * @param expectedRevision revision the caller believes is stored; {@code 0} creates
     * @return future completing with the outcome and the observed revision
     */
    CompletableFuture<DomainSaveResult> save(UUID player, String domain, String playerName,
            byte[] payload, int schemaVersion, long expectedRevision);

    /**
     * Lists players that have a stored payload in one domain.
     *
     * @param domain domain identifier
     * @return future completing with the known player UUIDs, possibly empty
     */
    CompletableFuture<List<UUID>> listPlayers(String domain);
}
