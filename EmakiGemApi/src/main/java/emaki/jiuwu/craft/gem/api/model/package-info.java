/**
 * Stable immutable value objects returned by EmakiGem's catalog, operations, and events.
 *
 * <h2>Ownership</h2>
 * Collections are immutable snapshots. {@code ItemStack} components are detached transaction outputs;
 * callers own writing them back to the appropriate inventory or container on its owner thread.
 *
 * <h2>Result semantics</h2>
 * A successful inlay returns {@code Success<GemInlayOutcome>}. A rolled failure that really consumed an
 * input returns {@code Partial<GemInlayOutcome>} rather than a fake success or a payload-less failure.
 * Extraction represents a destroyed return gem with nullable storage plus an Optional convenience
 * accessor; it never invents an AIR item.
 *
 * <h2>Threading</h2>
 * These immutable values are safe to read from any thread. Bukkit objects contained inside them must
 * still be used according to Bukkit/Paper/Folia ownership rules.
 */
package emaki.jiuwu.craft.gem.api.model;
