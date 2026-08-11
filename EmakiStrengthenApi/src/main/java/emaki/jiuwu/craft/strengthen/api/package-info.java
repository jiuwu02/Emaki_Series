/**
 * Public API for EmakiStrengthen recipes, previews, attempts, transfers, and item rebuilding.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi} is the entry point; its catalog and
 * operation layers are runtime-owned and are not third-party implementation contracts.
 *
 * <h2>Threading</h2>
 * Recipe-table reads may run anywhere. Player previews, attempts, transfers, GUI opens, and player refreshes
 * require the player's entity-owner thread. Item work inherits the owner-thread requirement of the inventory,
 * entity, or region holding the stack.
 *
 * <h2>Degradation</h2>
 * With no installed bridge, {@code status()} reports not installed, recipe queries are empty, and every
 * result-bearing action returns
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}.
 */
package emaki.jiuwu.craft.strengthen.api;
