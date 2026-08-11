/**
 * Attribute snapshots, resource definitions, damage contexts, and PDC payload value objects.
 *
 * <h2>Stability</h2>
 * Stable. These types are API data carriers rather than configuration internals.
 *
 * <h2>Threading</h2>
 * Detached snapshots and payloads may be read from any thread. A model that carries a live Bukkit entity
 * or {@code ItemStack} inherits that object's owner-thread requirement; callers must not treat a model as
 * permission to access the referenced Bukkit state off-thread.
 *
 * <h2>Degradation</h2>
 * These values are produced through the facade layers. When EmakiAttribute is unavailable, those layers
 * return {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} or empty queries rather
 * than manufacturing a business-shaped model.
 */
package emaki.jiuwu.craft.attribute.api.model;
