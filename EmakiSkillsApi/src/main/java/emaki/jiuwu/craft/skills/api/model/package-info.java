/**
 * Immutable skill-definition and player-skill view models.
 *
 * <h2>Stability</h2>
 * Stable. These models expose normalized API views rather than mutable skill configuration or live runtime
 * services.
 *
 * <h2>Threading</h2>
 * The models are safe to read from any thread after they have been returned. Producing a player view still
 * requires the owner-thread rules documented by the enclosing Skills API.
 *
 * <h2>Degradation</h2>
 * When EmakiSkills is unavailable, catalog lookups return empty views and operations return
 * {@link emaki.jiuwu.craft.corelib.api.contract.EmakiResult#unavailable()} rather than a fabricated model.
 */
package emaki.jiuwu.craft.skills.api.model;
