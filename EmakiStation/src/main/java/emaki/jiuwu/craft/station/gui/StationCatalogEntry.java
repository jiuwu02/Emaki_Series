package emaki.jiuwu.craft.station.gui;

import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

/**
 * One row of a station catalog, paired with whether the viewer has unlocked it.
 *
 * <h2>Visible but locked is a deliberate third state</h2>
 * A recipe can be absent from the catalog entirely ({@code visible: false}), present and usable, or present and
 * locked. The locked state exists so a player can see that progression continues past what they can currently
 * make; hiding those recipes would make the catalog silently shrink and grow.
 *
 * <p>A locked entry still occupies its slot and still shows its requirements, but clicking it does nothing.
 *
 * @param recipe   the recipe this row shows
 * @param unlocked whether the viewer satisfies the recipe's display condition
 */
public record StationCatalogEntry(RecipeDefinition recipe, boolean unlocked) {
}
