// SPDX-License-Identifier: MIT
/**
 * Stable public facade and capability interfaces for EmakiCooking.
 *
 * <h2>Layout</h2>
 * {@link emaki.jiuwu.craft.cooking.api.EmakiCookingApi#catalog()} exposes all seven station recipe
 * loaders and placed-station snapshots; {@code operations()} constructs outputs and evaluates completion
 * conditions; {@code nutrition()} exposes nutrition values and mutations.
 *
 * <h2>Threading</h2>
 * Recipe listing is read-only. Matching with a player and completion-condition evaluation require the
 * player's entity-owner thread. Station snapshots require the location-owner thread. Nutrition writes
 * for an online player require that player's owner thread. Ownership violations return
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD}.
 *
 * <h2>Availability</h2>
 * Use {@link emaki.jiuwu.craft.cooking.api.EmakiCookingApi#status()}. Reload keeps the bridge installed
 * but marks it not ready; business methods then return unavailable instead of observing half-loaded
 * recipe tables. Nutrition being disabled by configuration is a business rejection
 * ({@code REJECTED + cooking.nutrition.disabled}), not a separate failure kind.
 *
 * <h2>Dependency rule</h2>
 * Depend on {@code emaki-cooking-api} with Maven {@code provided} or Gradle {@code compileOnly}. Never
 * shade it, because EmakiCooking already carries an un-relocated API copy and Bukkit listener identity
 * depends on the exact event {@code Class} object.
 */
package emaki.jiuwu.craft.cooking.api;
