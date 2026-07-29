/**
 * Immutable value objects returned by the EmakiGem API.
 *
 * <h2>Stability</h2>
 * Stable. Every type is a record whose reference components are normalised in the canonical
 * constructor, so no accessor returns {@code null} except where explicitly annotated
 * {@link org.jetbrains.annotations.Nullable} — an absent gem in a socket, and an absent returned gem
 * after an extraction whose return mode destroys it.
 *
 * <h2>Levels are explicit</h2>
 * EmakiGem scales stats, attributes, and skills per gem level, so
 * {@link emaki.jiuwu.craft.gem.api.model.GemDefinitionView} records the level its values were resolved
 * for rather than presenting level-independent numbers.
 *
 * <h2>Socket state is complete</h2>
 * {@link emaki.jiuwu.craft.gem.api.model.GemStateView} lists every socket the equipment declares, not
 * only the occupied ones, so a caller can render the full socket strip without consulting the
 * definition table.
 *
 * <h2>Outcomes are already committed</h2>
 * {@link emaki.jiuwu.craft.gem.api.model.GemInlayOutcome} and
 * {@link emaki.jiuwu.craft.gem.api.model.GemExtractOutcome} are produced after EmakiGem has written the
 * gem layer, charged costs, run success actions, and closed its journal entry. The stacks they carry
 * are final; place them back where the originals came from.
 *
 * <h2>Threading</h2>
 * All types are safe to read from any thread.
 */
package emaki.jiuwu.craft.gem.api.model;
