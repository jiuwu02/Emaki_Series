/**
 * Public API for the EmakiCooking cooking and nutrition system.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.cooking.api.EmakiCookingApi} is the entry point;
 * {@link emaki.jiuwu.craft.cooking.api.CookingNutrition},
 * {@link emaki.jiuwu.craft.cooking.api.CookingCatalog}, and
 * {@link emaki.jiuwu.craft.cooking.api.CookingOperations} are
 * {@link org.jetbrains.annotations.ApiStatus.NonExtendable}.
 *
 * <h2>Two independent subsystems</h2>
 * Cooking stations and player nutrition ship together but are configured separately. A server owner can
 * disable nutrition while stations keep working, so {@code status().ready()} does not imply nutrition is
 * on: check {@link emaki.jiuwu.craft.cooking.api.CookingNutrition#enabled()}, or branch on
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#DISABLED}.
 *
 * <h2>Threading</h2>
 * Nutrition reads and the recipe tables may be read from any thread. Anything that touches a player or
 * a placed station must run on that object's owner thread and otherwise reports
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD}.
 *
 * <h2>What is not exposed, and why</h2>
 * <ul>
 * <li><strong>Reward delivery.</strong> EmakiCooking's delivery call returns {@code void}, completes on
 * an async chain, and swallows its own exceptions. Wrapping it would mean reporting success for work
 * that may silently fail, so it is omitted rather than misrepresented.</li>
 * <li><strong>Recipe configuration sections.</strong> A recipe's live YAML handle is an internal
 * structure server owners may restructure; only a recipe's stable identity is surfaced.</li>
 * <li><strong>Programmatic station operation.</strong> Stations are driven by block interaction
 * listeners and per-station tick loops, not by a callable entry point.</li>
 * </ul>
 *
 * <h2>Degradation</h2>
 * When EmakiCooking is absent, {@code status()} reports
 * {@link emaki.jiuwu.craft.corelib.api.contract.ApiStatus#notInstalled()}, queries answer empty, and
 * operations return {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()}.
 *
 * <h2>Do not shade</h2>
 * Use {@code provided} or {@code compileOnly}. EmakiCooking's jar already carries an un-relocated copy
 * of these classes.
 */
package emaki.jiuwu.craft.cooking.api;
