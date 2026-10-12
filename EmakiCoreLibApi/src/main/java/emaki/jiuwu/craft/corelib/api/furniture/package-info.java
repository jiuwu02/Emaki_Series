/**
 * Bridges and normalised events for third-party furniture providers.
 *
 * <h2>Stability</h2>
 * Experimental. These interfaces track upstream furniture plugins (ItemsAdder, Oraxen, Nexo,
 * CraftEngine) whose own APIs change without notice, so signatures here may follow. Implement a
 * bridge only if you provide furniture that Emaki modules should recognise, and subscribe a
 * listener when you need to react to furniture placement, interaction, or removal.
 *
 * <h2>Threading</h2>
 * Listeners are invoked on the thread of the underlying Bukkit event, which is the region owner
 * thread for the furniture's location. Keep handlers non-blocking and do not schedule from them.
 *
 * <h2>Degradation</h2>
 * When a furniture source is not installed its bridge reports {@code available() == false} and no
 * events are raised for it. A bridge that throws is treated as absent for that call, and a
 * listener that throws is isolated so the failure never propagates into the source plugin.
 */
package emaki.jiuwu.craft.corelib.api.furniture;
