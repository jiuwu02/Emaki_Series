/**
 * Bukkit events emitted by EmakiAttribute before resource, point, and damage mutations commit.
 *
 * <h2>Stability</h2>
 * Stable event contracts. Events are runtime-produced and are not third-party implementation points.
 *
 * <h2>Threading</h2>
 * Events fire synchronously on the owner thread of their live Bukkit target. On Paper this is the main
 * server thread; on Folia player events use the player's entity scheduler and damage events use the owner
 * thread shared by the combatants. Listeners must schedule later cross-owner work themselves.
 *
 * <h2>Degradation</h2>
 * When EmakiAttribute is unavailable, no event is emitted; facade operations return an explicit
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult} failure instead.
 */
package emaki.jiuwu.craft.attribute.api.event;
