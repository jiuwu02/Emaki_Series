/**
 * Stable Bukkit events fired by EmakiCooking.
 *
 * <h2>Events</h2>
 * {@link emaki.jiuwu.craft.cooking.api.event.CookingRecipeCompleteEvent} is cancellable and may redirect
 * result delivery. {@link emaki.jiuwu.craft.cooking.api.event.PlayerNutritionConsumeEvent} is
 * cancellable. Threshold events are read-only. Station interaction events feed
 * {@link emaki.jiuwu.craft.cooking.api.CookingCatalog#recentStation(java.util.UUID)}.
 *
 * <h2>Threading</h2>
 * Events are synchronous and are emitted only when the runtime owns the relevant player, location, or
 * global transaction boundary. Listener code must obey the same Paper/Folia ownership constraints.
 *
 * <h2>Availability</h2>
 * No event is synthesized while the API is unavailable or reloading. Facade operations communicate that
 * state through {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult}.
 */
package emaki.jiuwu.craft.cooking.api.event;
