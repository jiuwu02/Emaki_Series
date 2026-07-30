/**
 * Public API for the EmakiForge forging system.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.forge.api.EmakiForgeApi} is the entry point;
 * {@link emaki.jiuwu.craft.forge.api.ForgeCatalog} and
 * {@link emaki.jiuwu.craft.forge.api.ForgeOperations}, and
 * {@link emaki.jiuwu.craft.forge.api.ForgeExtensions} are
 * {@link org.jetbrains.annotations.ApiStatus.NonExtendable}.
 *
 * <h2>Threading</h2>
 * Catalog lookups that do not involve a player may be called from any thread. Synchronous methods that
 * touch a {@code Player} must run on that player's owner thread and otherwise report
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD}. Programmatic
 * {@code forgeAsync} accepts calls from any thread and performs its Bukkit phases on the player owner.
 *
 * <h2>Direct forge input ownership</h2>
 * Programmatic forging accepts detached escrow snapshots. The caller reserves the physical input
 * items before invoking the API and commits or releases that physical escrow from the returned
 * result; EmakiForge does not search arbitrary player inventory slots for equivalent stacks. Preview
 * never consumes escrow, runs configured actions, records mastery, or delivers an item.
 *
 * <h2>Degradation</h2>
 * When EmakiForge is absent, {@code status()} reports
 * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()}, catalog queries answer
 * empty, and operations return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}.
 *
 * <h2>Do not shade</h2>
 * Use {@code provided} or {@code compileOnly}. EmakiForge's jar already carries an un-relocated copy
 * of these classes; a duplicate would make your event listeners silently unreachable.
 */
package emaki.jiuwu.craft.forge.api;
