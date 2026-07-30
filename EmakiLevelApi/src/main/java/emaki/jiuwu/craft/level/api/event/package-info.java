/**
 * Bukkit events published by EmakiLevel for level and experience changes.
 *
 * <h2>Stability</h2>
 * Stable event contracts. They are emitted by EmakiLevel and are not third-party extension interfaces.
 *
 * <h2>Threading</h2>
 * Events concerning a player fire synchronously on that player's owner thread. Listeners may use the
 * supplied player only on that thread and must schedule any later cross-owner work themselves.
 *
 * <h2>Degradation</h2>
 * A missing or unavailable EmakiLevel bridge emits no events; API mutations instead complete with
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} and read layers remain empty.
 */
package emaki.jiuwu.craft.level.api.event;
