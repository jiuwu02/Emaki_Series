/**
 * Bukkit events emitted by the EmakiItem runtime.
 *
 * <p>{@link emaki.jiuwu.craft.item.api.event.EmakiItemCreateEvent} is a synchronous cancellable pre-commit
 * event fired on the global-region owner thread. Cancelling it prevents the built stack from leaving the
 * factory. {@link emaki.jiuwu.craft.item.api.event.ItemRepairEvent} is fired on the repaired player's
 * entity-owner thread before costs or durability are committed. {@link
 * emaki.jiuwu.craft.item.api.event.ItemSetBonusChangeEvent} is an entity-owner-thread post notification.
 *
 * <p>When the runtime bridge is absent no events are emitted. Do not shade the API artifact: Bukkit event
 * listeners are matched by class identity.
 */
package emaki.jiuwu.craft.item.api.event;
