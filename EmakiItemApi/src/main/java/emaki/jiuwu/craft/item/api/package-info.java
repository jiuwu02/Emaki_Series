/**
 * Public facade and capability layers for EmakiItem.
 *
 * <h2>Stability</h2>
 * The facade, catalog, operations, repair, and extension contracts are stable for the 2.7 API line.
 * Administrative migration is marked {@link org.jetbrains.annotations.ApiStatus.Experimental} because it
 * performs cross-module filesystem changes.
 *
 * <h2>Threading</h2>
 * Definition lookups are thread-agnostic. Player state, inventories, GUI operations, condition evaluation,
 * and economy repair require the target player's entity-owner thread. Creation requires global-region
 * ownership so its synchronous cancellable event is guaranteed to fire. Migration preview/apply are
 * synchronous file operations and should run on a caller-provided non-tick worker. Detached item stacks may
 * be processed directly; live inventory stacks remain the caller's ownership responsibility.
 *
 * <h2>Unavailable degradation</h2>
 * Accessors never return {@code null}. Empty collection/boolean answers are used only for explicitly cheap
 * probes such as {@code definitionIds()} and {@code exists()}; all result-bearing calls return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} while the bridge is absent.
 * Extension registration returns an inactive closeable handle.
 */
package emaki.jiuwu.craft.item.api;
