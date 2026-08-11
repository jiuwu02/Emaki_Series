/**
 * Immutable value objects returned by the EmakiForge API.
 *
 * <h2>Stability</h2>
 * Stable unless the type itself is marked {@link org.jetbrains.annotations.ApiStatus.Experimental}.
 * Reference components are normalised in canonical constructors; mutable item stacks are cloned at
 * the public boundary.
 *
 * <h2>Deliberately narrower than the runtime</h2>
 * These views omit condition blocks, action phase tables, failure outcome weights, and quality pools.
 * Those are EmakiForge's YAML configuration internals; exposing them would tie third parties to a
 * config shape that server owners are free to restructure.
 *
 * <h2>Item-stack snapshots</h2>
 * {@link emaki.jiuwu.craft.forge.api.model.ForgeInputs} and
 * {@link emaki.jiuwu.craft.forge.api.model.ForgeOutcome} clone mutable
 * {@link org.bukkit.inventory.ItemStack} values on construction and access.
 *
 * <h2>Threading</h2>
 * All types are safe to read from any thread. Producing them may require an owner thread; see
 * {@link emaki.jiuwu.craft.forge.api.ForgeCatalog}.
 *
 * <h2>Degradation</h2>
 * These values are returned only by a usable facade. An unavailable EmakiForge reports empty catalog
 * queries or {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} rather than a
 * fabricated forge view.
 */
package emaki.jiuwu.craft.forge.api.model;
