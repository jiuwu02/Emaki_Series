/**
 * Public API for EmakiLevel definitions, player progress, synchronous mutations, and experience sources.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.level.api.EmakiLevelApi} is the entry point; catalog, operation, and
 * extension layers are runtime-owned. {@link emaki.jiuwu.craft.level.api.ExpSourceProvider} is the explicit
 * third-party implementation point.
 *
 * <h2>Threading</h2>
 * Loaded type and ranking queries may run anywhere. Reads and writes involving a live player must run on
 * that player's owner thread. Experience-source registration may run anywhere; provider callbacks inherit
 * the thread of the experience grant that invokes them.
 *
 * <h2>Degradation</h2>
 * Without EmakiLevel, {@code status()} reports not installed, catalog queries are empty, operations return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}, and source registration returns
 * an inactive closeable handle.
 */
package emaki.jiuwu.craft.level.api;
