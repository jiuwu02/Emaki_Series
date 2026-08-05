/**
 * Public API surface for EmakiAccessory.
 *
 * <p>Entry point is {@link emaki.jiuwu.craft.accessory.api.EmakiAccessoryApi}. Accessory slots are
 * plain strings owned by this module rather than {@link org.bukkit.inventory.EquipmentSlot} values,
 * because a Bukkit equipment slot has a backing container on the server and an accessory slot does
 * not; the items live in this plugin's own per-player storage.
 */
package emaki.jiuwu.craft.accessory.api;
