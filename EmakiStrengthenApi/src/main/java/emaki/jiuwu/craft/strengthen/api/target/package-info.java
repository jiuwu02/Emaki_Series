/**
 * Enhancement target extension point.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider} is meant to be
 * implemented by target-owning plugins; the enhancement recipe structure and the registry itself
 * remain internal to EmakiStrengthen.
 *
 * <h2>Threading</h2>
 * Provider methods are called on the owner thread of whatever holds the stack being read or written.
 * Registration and removal are safe from any thread.
 *
 * <h2>Degradation</h2>
 * When the runtime bridge is absent, registration returns
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} and no provider is
 * recorded, so a caller must not assume its target type became available.
 */
package emaki.jiuwu.craft.strengthen.api.target;
