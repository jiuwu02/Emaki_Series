/**
 * Public API for the EmakiGem socket and gem system.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.gem.api.EmakiGemApi} is the entry point;
 * {@link emaki.jiuwu.craft.gem.api.GemCatalog} and {@link emaki.jiuwu.craft.gem.api.GemOperations} are
 * {@link org.jetbrains.annotations.ApiStatus.NonExtendable}.
 *
 * <h2>Threading</h2>
 * Catalog queries read definition tables and item persistent data, and may be called from any thread.
 * Every operation must be called on the thread that owns the acting player.
 *
 * <p>The operation restriction is enforced, not advisory: EmakiGem guards its own event fire points
 * with an ownership check and silently skips the event when that check fails, so an off-thread write
 * would commit without ever letting listeners cancel it. Such calls therefore report
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD} and change nothing.
 *
 * <h2>Transactions</h2>
 * EmakiGem journals inlay and extraction so a crash mid-operation can be compensated on the next
 * start. Its internal calls hand back a pending commit action; this API always performs that commit
 * before returning and never exposes the action. Skipping it would leave the journal entry marked
 * "charged but unfinished", and the next start would refund a player whose gem was in fact inlaid.
 *
 * <h2>Cancellation is not distinguishable from rejection</h2>
 * EmakiGem reports a listener veto and an unmet precondition with the same reason key, so both surface
 * as {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#REJECTED}. Listen to the pre-events
 * yourself if you need to tell them apart.
 *
 * <h2>Degradation</h2>
 * When EmakiGem is absent, {@code status()} reports
 * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()}, catalog queries answer
 * empty, and operations return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}.
 *
 * <h2>Do not shade</h2>
 * Use {@code provided} or {@code compileOnly}. EmakiGem's jar already carries an un-relocated copy of
 * these classes.
 */
package emaki.jiuwu.craft.gem.api;
