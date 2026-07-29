/**
 * Immutable value objects returned by the EmakiCooking API.
 *
 * <h2>Stability</h2>
 * Stable. Every type normalises its reference components in the canonical constructor, so no accessor
 * returns {@code null} except where explicitly annotated {@link org.jetbrains.annotations.Nullable}.
 *
 * <h2>Sparse station readings</h2>
 * EmakiCooking stores all seven station kinds in one wide structure where each kind fills only the
 * fields it uses and leaves the rest at zero, which makes a raw {@code 0} ambiguous: an oven at zero
 * heat and a chopping board that has no concept of heat look identical.
 * {@link emaki.jiuwu.craft.cooking.api.model.CookingStationView} therefore exposes heat, moisture,
 * steam, and fluid volume as {@link java.util.OptionalInt}, present only when the station kind actually
 * tracks that reading.
 *
 * <h2>Station identity</h2>
 * Persist {@link emaki.jiuwu.craft.cooking.api.model.CookingStationType#configKey()} rather than
 * {@code name()}. The config key is what appears in configuration files and in the {@code stationType}
 * field of EmakiCooking's events.
 *
 * <h2>Threading</h2>
 * All types are safe to read from any thread.
 */
package emaki.jiuwu.craft.cooking.api.model;
