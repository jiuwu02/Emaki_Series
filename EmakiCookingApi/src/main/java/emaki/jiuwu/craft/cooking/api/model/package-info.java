/**
 * Stable immutable value objects for EmakiCooking.
 *
 * <h2>Station semantics</h2>
 * {@link emaki.jiuwu.craft.cooking.api.model.CookingStationType} contains exactly the seven runtime
 * station kinds. Snapshot readings that a station does not track use {@code OptionalInt.empty()} rather
 * than a fake zero. Unknown runtime station identifiers fail mapping; they never fall back to WOK.
 *
 * <h2>Result semantics</h2>
 * Nutrition value zero and completion-condition false are legitimate payloads inside
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult}, distinct from unavailable/not-found
 * failures. A partial output construction carries the items that were actually created.
 *
 * <h2>Threading</h2>
 * These immutable values are safe to read from any thread. Contained Bukkit objects remain subject to
 * Bukkit/Paper/Folia ownership rules.
 */
package emaki.jiuwu.craft.cooking.api.model;
