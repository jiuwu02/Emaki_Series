/**
 * Public API for the EmakiKits kit, reward and CDK system.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.kits.api.EmakiKitsApi} is the entry point;
 * {@link emaki.jiuwu.craft.kits.api.KitCatalog},
 * {@link emaki.jiuwu.craft.kits.api.KitOperations} and
 * {@link emaki.jiuwu.craft.kits.api.KitExtensions} are
 * {@link org.jetbrains.annotations.ApiStatus.NonExtendable}.
 *
 * <h2>Threading</h2>
 * Catalog lookups that do not involve a player may be called from any thread. Operations that take
 * a {@code Player} must run on that player's owner thread and otherwise report
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD}. Operations are
 * synchronous; the on-disk persistence flush may be deferred to the configured autosave.
 *
 * <h2>Idempotency</h2>
 * Claims, purchases, CDK redemptions and open-count milestones all pass through persisted
 * idempotency keys. Concurrent calls, reloads, restarts and repeated commands cannot grant the same
 * reward twice. An unavailable plugin is never represented as a business outcome.
 *
 * <h2>Degradation</h2>
 * When EmakiKits is absent, {@code status()} reports
 * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()}, catalog queries answer
 * empty, and operations return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}.
 *
 * <h2>Do not shade</h2>
 * Use {@code provided} or {@code compileOnly}. EmakiKits' jar already carries an un-relocated copy
 * of these classes; a duplicate would make your event listeners silently unreachable.
 */
package emaki.jiuwu.craft.kits.api;
