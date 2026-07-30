/**
 * Strengthen recipe, state, preview, attempt, and transfer value models.
 *
 * <h2>Stability</h2>
 * Stable. These are public operation inputs and outputs; raw recipe configuration and runtime transaction
 * state remain internal.
 *
 * <h2>Threading</h2>
 * Detached values may be read from any thread. Contexts or outcomes that contain an {@code ItemStack} must
 * be used on the owner thread when that stack belongs to a live inventory or entity.
 *
 * <h2>Degradation</h2>
 * EmakiStrengthen creates these values only through successful catalog or operation calls. An unavailable
 * API returns {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} rather than a
 * default recipe, preview, or outcome.
 */
package emaki.jiuwu.craft.strengthen.api.model;
