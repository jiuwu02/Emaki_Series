/**
 * Bukkit events published by EmakiSkills for casts, upgrades, and slot changes.
 *
 * <p>Pre events are cancellable and fire before mutation. Post events are informational and fire only after
 * the corresponding state/effect commit. Every event touching a player is fired synchronously on that
 * player's owner thread.
 *
 * <p>A missing or unavailable Skills bridge emits no events. Do not shade the API artifact because Bukkit
 * listener dispatch depends on class identity.
 */
package emaki.jiuwu.craft.skills.api.event;
