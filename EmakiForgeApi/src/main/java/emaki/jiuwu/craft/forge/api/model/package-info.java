/**
 * Immutable value objects returned by the EmakiForge API.
 *
 * <h2>Stability</h2>
 * Stable. Every type here is a record whose reference components are normalised in the canonical
 * constructor, so no accessor returns {@code null} and every collection is immutable.
 *
 * <h2>Deliberately narrower than the runtime</h2>
 * These views omit condition blocks, action phase tables, failure outcome weights, and quality pools.
 * Those are EmakiForge's YAML configuration internals; exposing them would tie third parties to a
 * config shape that server owners are free to restructure.
 *
 * <h2>Item stacks are not cloned</h2>
 * {@link emaki.jiuwu.craft.forge.api.model.ForgeInputs} copies its maps but keeps the caller's
 * {@link org.bukkit.inventory.ItemStack} references. Do not mutate a stack after handing it over.
 *
 * <h2>Threading</h2>
 * All types are safe to read from any thread. Producing them may require an owner thread; see
 * {@link emaki.jiuwu.craft.forge.api.ForgeCatalog}.
 */
package emaki.jiuwu.craft.forge.api.model;
