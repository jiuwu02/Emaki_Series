/**
 * Bukkit events published around EmakiStrengthen attempts and star transfers.
 *
 * <h2>Stability</h2>
 * Stable event contracts. Pre-events are cancellable and post-events are informational; none is a
 * third-party implementation interface.
 *
 * <h2>Threading</h2>
 * Events fire synchronously on the acting player's entity-owner thread. On Folia that is the player's
 * region thread, so listeners must keep all related Bukkit access on that owner.
 *
 * <h2>Degradation</h2>
 * When EmakiStrengthen is unavailable, no attempt or transfer event is emitted. Facade calls instead return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} and catalog queries remain
 * empty.
 */
package emaki.jiuwu.craft.strengthen.api.event;
