/**
 * Action pipeline contracts for sources, gates, actions, and triggers.
 *
 * <p>Stages declare scheduler ownership; async stages cannot require Bukkit targets. Registrations are
 * owner-scoped, must be closed on disable, and become inactive when CoreLib is unavailable.
 */
package emaki.jiuwu.craft.corelib.api.action;
