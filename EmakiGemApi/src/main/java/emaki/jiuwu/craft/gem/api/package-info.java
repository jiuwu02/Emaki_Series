// SPDX-License-Identifier: MIT
/**
 * Stable public facade and capability interfaces for EmakiGem.
 *
 * <h2>Stability</h2>
 * The facade, catalog, operation interfaces, result payloads, and event identities are public API.
 * Lifecycle hooks and bridge implementation contracts are internal/non-extendable as marked with
 * JetBrains {@code ApiStatus} annotations.
 *
 * <h2>Threading</h2>
 * Catalog queries may inspect detached item snapshots from any thread. Player-scoped operations must be
 * called from the player's entity-owner thread; item transforms must run on the owner thread of the
 * inventory, entity, or container that owns the item. Wrong-thread calls return
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD}.
 *
 * <h2>Availability and results</h2>
 * Use {@link emaki.jiuwu.craft.gem.api.EmakiGemApi#status()} to distinguish installed and ready states.
 * During reload the bridge stays installed but is not ready. Fallible operations use
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult}; no method reports absence through a fake
 * successful value. Void-like operations use
 * {@link emaki.jiuwu.craft.corelib.api.contract.Unit}.
 *
 * <h2>Transactions</h2>
 * Inlay/extract commit actions are internal. Public operations commit them before returning their final
 * equipment payload. Completed events are emitted only after success actions finish and the operation
 * journal reaches its terminal completed phase.
 *
 * <h2>Dependency rule</h2>
 * Depend on {@code emaki-gem-api} with Maven {@code provided} or Gradle {@code compileOnly}. Do not
 * shade it: EmakiGem already carries an un-relocated API copy, and duplicate Bukkit event classes break
 * listener identity silently.
 */
package emaki.jiuwu.craft.gem.api;
