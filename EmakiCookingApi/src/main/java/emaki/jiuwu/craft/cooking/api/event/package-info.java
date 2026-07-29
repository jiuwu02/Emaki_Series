/**
 * Bukkit events fired by EmakiCooking.
 *
 * <h2>The four events</h2>
 * {@link emaki.jiuwu.craft.cooking.api.event.CookingRecipeCompleteEvent} is cancellable and can also
 * redirect whether the result drops or goes to the player's inventory.
 * {@link emaki.jiuwu.craft.cooking.api.event.PlayerNutritionConsumeEvent} is cancellable and vetoes a
 * food item's nutrition effects. {@link emaki.jiuwu.craft.cooking.api.event.NutritionThresholdChangeEvent}
 * and {@link emaki.jiuwu.craft.cooking.api.event.CookingStationInteractEvent} are informational.
 *
 * <h2>Threading</h2>
 * All four are synchronous. The two nutrition events and the station interaction event fire on the
 * thread that owns the player; recipe completion fires on the global region thread, because reward
 * delivery is world-scoped rather than player-scoped.
 *
 * <h2>Coverage — read this before building an audit trail</h2>
 * Every fire point is guarded by a thread-ownership check and is <em>silently skipped</em> when the
 * check fails, while the underlying work still proceeds. In particular
 * {@code CookingRecipeCompleteEvent} requires global-region ownership, so a recipe that completes while
 * that ownership is unavailable produces output without an event.
 *
 * <p>{@code NutritionThresholdChangeEvent} distinguishes two shapes through its
 * {@code Kind}: for {@code SINGLE} the type id and current value are meaningful and the match counters
 * are zero; for {@code COMBO} the type id is {@code null}, the value is zero, and the counters carry the
 * information. Read the kind before reading the other fields.
 *
 * <h2>Do not shade</h2>
 * Bukkit matches listeners by {@code Class} identity. If you shade {@code emaki-cooking-api} into your
 * jar, your listener will reference a different {@code Class} object than the one EmakiCooking fires and
 * your handlers will never run — silently, with nothing logged. Always depend on this artifact with
 * {@code provided} or {@code compileOnly}.
 */
package emaki.jiuwu.craft.cooking.api.event;
