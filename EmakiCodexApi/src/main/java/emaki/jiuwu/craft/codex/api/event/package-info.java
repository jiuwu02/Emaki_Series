/**
 * Bukkit events published by EmakiCodex.
 *
 * <p>{@link emaki.jiuwu.craft.codex.api.event.AdvancementGrantEvent} and
 * {@link emaki.jiuwu.craft.codex.api.event.AdvancementRevokeEvent} are cancellable pre-mutation events for
 * every EmakiCodex mutation path, including commands, actions, configured triggers, external trigger
 * providers, and the public operations API.
 *
 * <p>{@link emaki.jiuwu.craft.codex.api.event.AdvancementCompletedEvent} is informational and is emitted
 * from the actual Bukkit advancement-completion event for every advancement registered by EmakiCodex. This
 * also observes criteria awarded by another source. All three events fire on the target player's owner
 * thread. Do not shade the API artifact because Bukkit dispatch depends on class identity.
 */
package emaki.jiuwu.craft.codex.api.event;
